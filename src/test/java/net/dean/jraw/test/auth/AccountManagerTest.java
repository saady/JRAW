package net.dean.jraw.test.auth;

import net.dean.jraw.ApiException;
import net.dean.jraw.AppType;
import net.dean.jraw.JrawUtils;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.FlairTemplate;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.models.VoteDirection;
import net.dean.jraw.paginators.Paginator;
import net.dean.jraw.paginators.Paginators;
import net.dean.jraw.paginators.SubredditPaginator;
import net.dean.jraw.paginators.UserContributionPaginator;
import net.dean.jraw.paginators.UserSubredditsPaginator;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.List;

import static org.testng.Assert.*;

/**
 * This class tests methods that require authentication, such as voting, saving, hiding, and posting.
 */
public class AccountManagerTest extends AuthenticatedRedditTest {
    private static final String SUBMISSION_ID = "2kx1ly";
    private static final String COMMENT_ID = "clpgpjk";
    private static String CLIENT_ID = "0fehncPayYTIIg";
    private static String DEV_NAME = "jraw_test2";
    private String newSubmssionId;
    private String newCommentId;

    @Test
    public void testPostLink() {
        try {
            long number = epochMillis();

            URL url = JrawUtils.newUrl("https://www." + number + ".com");

            Submission submission = account.submit(
                    new AccountManager.SubmissionBuilder(url, "jraw_testing2", "Link post test (epoch=" + number + ")"));

            assertTrue(!submission.isSelfPost());
            assertTrue(submission.getUrl().equals(url.toExternalForm()));
            validateModel(submission);
            this.newSubmssionId = submission.getFullName();
        } catch (NetworkException e) {
            handle(e);
        } catch (ApiException e) {
            handlePostingQuota(e);
        }
    }

    @Test
    public void testPostSelfPost() {
        try {
            long number = epochMillis();
            String content = reddit.getUserAgent();

            Submission submission = account.submit(
                    new AccountManager.SubmissionBuilder(content, "jraw_testing2", "Self post test (epoch=" + number + ")"));

            assertTrue(submission.isSelfPost());
            assertTrue(submission.getSelftext().md().equals(content));
            validateModel(submission);
        } catch (NetworkException e) {
            handle(e);
        } catch (ApiException e) {
            handlePostingQuota(e);
        }
    }

    @Test
    public void testEditUserText() {
        String newText = "This is a new piece of text.";

        UserContributionPaginator p = getPaginator("submitted");
        p.setLimit(Paginator.RECOMMENDED_MAX_LIMIT);

        Listing<Contribution> submissions = p.next();
        Submission toEdit = null;
        for (Contribution c : submissions) {
            Submission s = (Submission) c;
            if (s.isSelfPost()) {
                toEdit = s;
            }
        }

        if (toEdit == null) {
            throw new IllegalStateException("Could not find any recent self posts");
        }

        try {
            account.updateSelfpost(toEdit, newText);
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test(expectedExceptions = {ApiException.class, SkipException.class})
    public void testPostWithInvalidCaptcha() throws ApiException {
        try {
            if (!reddit.needsCaptcha()) {
                throw new SkipException("No captcha needed, request will return successfully either way");
            }
            account.submit(
                    new AccountManager.SubmissionBuilder("content", "jraw_testing2", "title"), reddit.getNewCaptcha(), "invalid captcha attempt");
        } catch (NetworkException e) {
            handle(e);
        } catch (ApiException e) {
            if (isRateLimit(e)) {
                // Nothing we can really do about this
                handlePostingQuota(e);
            }
            if (e.getReason().equals("BAD_CAPTCHA")) {
                // What we want
                throw e;
            }
            // Some other reason
            handle(e);
        }
    }

    @Test
    public void testReplySubmission() {
        try {
            String replyText = "" + epochMillis();
            Submission submission = reddit.getSubmission(SUBMISSION_ID);

            // Reply to a submission
            this.newCommentId = account.reply(submission, replyText);
            assertTrue(JrawUtils.isFullName(newCommentId));
        } catch (ApiException e) {
            handlePostingQuota(e);
        } catch (NetworkException e) {
            handle(e);
        }
    }

    @Test
    public void testReplyComment() {
        try {
            Listing<Comment> comments = reddit.getSubmission(SUBMISSION_ID).getComments();
            Comment replyTo = null;
            for (Comment c : comments) {
                if (c.getId().equals(COMMENT_ID)) {
                    replyTo = c;
                    break;
                }
            }

            assertNotNull(replyTo);
            assertNotNull(account.reply(replyTo, "" + epochMillis()));
        } catch (NetworkException e) {
            handle(e);
        } catch (ApiException e) {
            handlePostingQuota(e);
        }
    }

    @Test(dependsOnMethods = "testReplySubmission")
    public void testDeleteComment() {
        try {
            account.delete(newCommentId);

            for (Comment c : reddit.getSubmission(SUBMISSION_ID).getComments()) {
                if (c.getId().equals(newCommentId)) {
                    fail("Found the (supposedly) deleted comment");
                }
            }
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test(dependsOnMethods = "testPostLink")
    public void testDeletePost() {
        try {
            account.delete(newSubmssionId);
        } catch (NetworkException | ApiException e) {
            handle(e);
        }

        try {
            reddit.getSubmission(newSubmssionId);
        } catch (NetworkException e) {
            if (e.getCode() != 404) {
                fail("Did not get a 404 when querying the deleted submission", e);
            }
        }
    }


    @Test
    public void testSendRepliesToInbox() throws ApiException {
        try {
            Submission s = (Submission) getPaginator("submitted").next().get(0);
            account.sendRepliesToInbox(s, true);
        } catch (NetworkException e) {
            handle(e);
        }
    }

    @Test
    public void testVote() {
        try {
            String submissionId = "28d6vv";
            Submission submission = reddit.getSubmission(submissionId);

            // Figure out a new vote direction: up if there is no vote, no vote if upvoted
            VoteDirection newVoteDirection = submission.getVote() == VoteDirection.NO_VOTE ? VoteDirection.UPVOTE : VoteDirection.NO_VOTE;
            account.vote(submission, newVoteDirection);

            submission = reddit.getSubmission(submissionId);
            // Make sure the vote took effect
            assertEquals(submission.getVote(), newVoteDirection);
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test
    public void testSaveSubmission() {
        try {
            Submission submission = reddit.getSubmission("28d6vv");
            account.save(submission);

            UserContributionPaginator paginator = getPaginator("saved");
            List<Contribution> saved = paginator.next();

            for (Contribution c : saved) {
                Submission s = (Submission) c;
                if (s.getId().equals(submission.getId())) {
                    return;
                }
            }

            fail("Did not find saved submission");

        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test(dependsOnMethods = "testSaveSubmission")
    public void testUnsaveSubmission() {
        try {
            Submission submission = reddit.getSubmission("28d6vv");
            account.unsave(submission);

            UserContributionPaginator paginator = getPaginator("saved");
            List<Contribution> saved = paginator.next();

            // Fail if we find the submission in the list
            for (Contribution s : saved) {
                if (s.getId().equals(submission.getId())) {
                    fail("Found the submission after it was unsaved");
                }
            }
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test
    public void testHideSubmission() {
        try {
            Submission submission = reddit.getSubmission("28d6vv");
            account.hide(submission, true);

            UserContributionPaginator paginator = getPaginator("hidden");
            List<Contribution> hidden = paginator.next();

            for (Contribution c : hidden) {
                Submission s = (Submission) c;
                if (s.getId().equals(submission.getId())) {
                    return;
                }
            }

            fail("Did not find the submission in the hidden posts");
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test(dependsOnMethods = "testHideSubmission")
    public void testUnhideSubmission() {
        try {
            Submission submission = reddit.getSubmission("28d6vv");
            account.hide(submission, false);

            UserContributionPaginator paginator = getPaginator("hidden");
            List<Contribution> hidden = paginator.next();

            for (Contribution c : hidden) {
                Submission s = (Submission) c;
                if (s.getId().equals(submission.getId())) {
                    fail("Found unhidden submission in hidden posts");
                }
            }
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test
    public void testAddDeveloper() {
        try {
            // Remove the developer to prevent /api/adddeveloper from returning a DEVELOPER_ALREADY_ADDED error.
            // /api/removedeveloper doesn't seem to return an error if the given name isn't in the list of current devs,
            // so this call will (probably) never fail.
            account.removeDeveloper(CLIENT_ID, DEV_NAME);
            // Actually test the method
            account.addDeveloper(CLIENT_ID, DEV_NAME);
        } catch (ApiException | NetworkException e) {
            handle(e);
        }
    }

    @Test
    public void testRemoveDeveloper() {
        // Add the developer if they're not already one
        try {
            account.addDeveloper(CLIENT_ID, DEV_NAME);
        } catch (ApiException e) {
            if (!e.getReason().equals("DEVELOPER_ALREADY_ADDED")) {
                // Not ok
                handle(e);
            }
        } catch (NetworkException e) {
            handle(e);
        }

        try {
            account.removeDeveloper(CLIENT_ID, DEV_NAME);
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test
    public void testCreateOrUpdateApp() {
        try {
            account.createOrUpdateApp(null,
                    "Test app for " + getClass().getSimpleName(),
                    AppType.SCRIPT,
                    "description goes here",
                    "https://github.com/thatJavaNerd/JRAW",
                    "https://github.com/thatJavaNerd/JRAW");
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    /*
    Note: It is impossible to test account.deleteApp(String) because there is no way (to my knowledge) to get the ID of
          any of your own apps, including the one created using account.createOrUpdateApp(null, <...>) because the
          response does not contain any of that data
     */

    @Test
    public void testSetNsfw() {
        try {
            Submission s = (Submission) getPaginator("submitted").next().get(0);
            boolean newVal = !s.isNsfw();

            account.setNsfw(s, newVal);

            // Reload the submission's data
            s = reddit.getSubmission(s.getId());
            assertTrue(s.isNsfw() == newVal);
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test
    public void testSubscribe() {
        try {
            Subreddit subreddit = reddit.getSubreddit("programming");
            boolean isSubscribed = isSubscribed(subreddit.getDisplayName());
            boolean expected = !isSubscribed;

            if (isSubscribed) {
                account.unsubscribe(subreddit);
            } else {
                account.subscribe(subreddit);
            }
            boolean actual = isSubscribed(subreddit.getDisplayName());
            assertEquals(actual, expected);
        } catch (NetworkException e) {
            handle(e);
        }
    }

    @Test
    public void testSticky() throws NetworkException {
        String modOf = getModeratedSubreddit().getDisplayName();
        SubredditPaginator paginator = Paginators.subreddit(reddit, modOf);

        Submission submission = null;
        List<Listing<Submission>> listingList = paginator.accumulate(3);
        for (Listing<Submission> submissions : listingList) {
            if (submissions.get(0).isStickied()) {
                // There is already a stickied post
                submission = submissions.get(0);
            } else {
                // Find the first self post
                for (Submission s : submissions) {
                    if (s.isSelfPost()) {
                        submission = s;
                        break;
                    }
                }
            }
        }

        if (submission == null)
            throw new IllegalStateException("No self posts in " + modOf);

        boolean expected = !(submission.isStickied());
        try {
            account.setSticky(submission, expected);
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test
    public void testGetFlairChoices() {
        try {
            String subreddit = "pcmasterrace"; // Glorious!
            List<FlairTemplate> templates = account.getFlairChoices(subreddit);
            validateModels(templates);

            validateModel(account.getCurrentFlair(subreddit));
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test
    public void testSetFlair() {
        try {
            String subreddit = "jraw_testing2";
            FlairTemplate template = account.getFlairChoices(subreddit).get(0);

            account.setFlair(subreddit, template, null);
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    @Test
    public void testEnableFlair() {
        try {
            account.setFlairEnabled("jraw_testing2", true);
        } catch (NetworkException | ApiException e) {
            handle(e);
        }
    }

    private boolean isSubscribed(String subreddit) {
        UserSubredditsPaginator paginator = Paginators.mySubreddits(reddit, "subscriber");
        paginator.setLimit(Paginator.RECOMMENDED_MAX_LIMIT);

        // Try to find the subreddit in the list of subscribed subs
        while (paginator.hasNext()) {
            Listing<Subreddit> subscribed = paginator.next();
            for (Subreddit sub : subscribed) {
                if (sub.getDisplayName().equals(subreddit)) {
                    return true;
                }
            }
        }

        return false;
    }

    private UserContributionPaginator getPaginator(String where) {
        return Paginators.contributions(reddit, reddit.getAuthenticatedUser(), where);
    }
}

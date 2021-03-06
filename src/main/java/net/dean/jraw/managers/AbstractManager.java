package net.dean.jraw.managers;

import net.dean.jraw.RedditClient;
import net.dean.jraw.http.HttpClient;
import net.dean.jraw.http.NetworkAccessible;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.RedditResponse;
import net.dean.jraw.http.RestRequest;

/**
 * This class serves as the base class for all "manager" classes, which have control over a certain section of the API,
 * such as multireddits, wikis, and messages
 */
public abstract class AbstractManager implements HttpClient<RedditResponse>,
        NetworkAccessible<RedditResponse, RedditClient> {
    protected final RedditClient reddit;

    /**
     * Instantiates a new AbstractManager
     * @param reddit The RedditClient to use
     */
    protected AbstractManager(RedditClient reddit) {
        this.reddit = reddit;
    }

    @Override
    public RedditResponse executeWithBasicAuth(RestRequest request, String username, String password) throws NetworkException {
        return reddit.executeWithBasicAuth(request, username, password);
    }

    @Override
    public final RedditResponse execute(RestRequest r) throws NetworkException {
        if (r.needsAuth() && !reddit.isLoggedIn()) {
            throw new IllegalStateException("This manager requires an authenticated user");
        }

        return reddit.execute(r);
    }

    @Override
    public final RestRequest.Builder request() {
        RestRequest.Builder b = getHttpClient().request();
        b.needsAuth(true); // Assuming needs authentication by default
        return b;
    }

    @Override
    public final RedditClient getHttpClient() {
        return reddit;
    }
}

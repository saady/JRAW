package net.dean.jraw.paginators;

import net.dean.jraw.EndpointImplementation;
import net.dean.jraw.Endpoints;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Message;

/**
 * Provides a way to iterate over a user's inbox
 */
public class InboxPaginator extends GenericPaginator<Message> {
    /**
     * Instantiates a new InboxPaginator
     *
     * @param reddit The client to send requests with
     * @param where  The "where" enum value to use
     */
    InboxPaginator(RedditClient reddit, String where) {
        super(reddit, Message.class, where);
    }

    @Override
    protected String getUriPrefix() {
        return "/message";
    }

    @Override
    public String[] getWhereValues() {
        return new String[] {"inbox", "unread", "messages", "sent", "moderator", "moderator/unread"};
    }

    @Override
    @EndpointImplementation({
            Endpoints.MESSAGE_INBOX,
            Endpoints.MESSAGE_SENT,
            Endpoints.MESSAGE_UNREAD,
            Endpoints.MESSAGE_WHERE
    })
    protected Listing<Message> getListing(boolean forwards) throws NetworkException {
        // Just call super so that we can add the @EndpointImplementation annotation
        return super.getListing(forwards);
    }
}

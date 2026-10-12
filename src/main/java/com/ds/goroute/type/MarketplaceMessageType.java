package com.ds.goroute.type;

public enum MarketplaceMessageType {
    TEXT,
    IMAGE,
    FILE,
    SYSTEM,
    LOCATION,
    /** A reference to a place, dish, check-in, trip, event or meetup; see {@link ChatShareType}. */
    SHARE,
    /**
     * An answer from the assistant (Pengoo): Markdown text plus blocks in the attachments
     * (THINKING, PLACE, PROPOSAL, SUGGESTIONS, WELCOME, NOTICE). Only the server writes it.
     */
    ASSISTANT
}

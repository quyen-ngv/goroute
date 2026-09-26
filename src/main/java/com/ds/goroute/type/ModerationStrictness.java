package com.ds.goroute.type;

/** Configured strictness for a {@link ModerationVisibility} tier. */
public enum ModerationStrictness {
    /** Keyword list plus the AI layer; block according to the policy table. */
    FULL,
    /** Keyword list only, and BLOCK is downgraded to FLAG unless the group is severe. */
    KEYWORD_ONLY,
    /**
     * Keyword list only, blocking on the spot, and never flagging.
     *
     * <p>The tier for private conversation. A flag would be useless there anyway -- it
     * exists to queue content for a human, and no human is allowed to read these threads --
     * and it would quietly store a copy of the message in the review queue, which is the
     * exact thing the privacy rule forbids. So the filter either refuses the message to the
     * person typing it or stays out of the way.
     */
    KEYWORD_BLOCK_ONLY,
    /** No proactive filtering; content is handled through user reports. */
    REPORT_ONLY,
    /** Not filtered at all. */
    OFF;

    public boolean runsKeywordLayer() {
        return this == FULL || this == KEYWORD_ONLY || this == KEYWORD_BLOCK_ONLY;
    }

    public boolean runsAiLayer() {
        return this == FULL;
    }

    /** Whether a BLOCK from the term list survives as a block, or softens into a flag. */
    public boolean keepsBlocks() {
        return this == FULL || this == KEYWORD_BLOCK_ONLY;
    }

    /**
     * Whether a verdict may be parked for a human to read. False for chat: the queue stores
     * the offending text, and private conversation must not leave a copy behind.
     */
    public boolean recordsFlags() {
        return this != KEYWORD_BLOCK_ONLY;
    }
}

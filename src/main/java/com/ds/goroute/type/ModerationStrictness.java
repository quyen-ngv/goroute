package com.ds.goroute.type;

/** Configured strictness for a {@link ModerationVisibility} tier. */
public enum ModerationStrictness {
    /** Keyword list plus the AI layer; block according to the policy table. */
    FULL,
    /** Keyword list only, and BLOCK is downgraded to FLAG unless the group is severe. */
    KEYWORD_ONLY,
    /** No proactive filtering; content is handled through user reports. */
    REPORT_ONLY,
    /** Not filtered at all. */
    OFF;

    public boolean runsKeywordLayer() {
        return this == FULL || this == KEYWORD_ONLY;
    }

    public boolean runsAiLayer() {
        return this == FULL;
    }
}

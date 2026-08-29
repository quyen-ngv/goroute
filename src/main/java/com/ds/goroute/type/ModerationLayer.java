package com.ds.goroute.type;

/** Which filter produced a decision. Needed to tell false positives apart in MOD-08. */
public enum ModerationLayer {
    KEYWORD,
    AI_TEXT,
    IMAGE
}

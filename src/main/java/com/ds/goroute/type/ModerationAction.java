package com.ds.goroute.type;

/** The three policy outcomes plus the no-violation case. */
public enum ModerationAction {
    /** Reject now, store nothing, tell the user which group was violated. */
    BLOCK,
    /** Publish, but queue it for a human (MOD-06). */
    FLAG,
    /** Publish; only record the decision for analysis (MOD-08). */
    LOG,
    /** No violation. */
    ALLOW;

    public boolean blocks() {
        return this == BLOCK;
    }

    /** BLOCK is the strongest; ALLOW the weakest. Used to merge per-field verdicts. */
    public int weight() {
        return switch (this) {
            case BLOCK -> 3;
            case FLAG -> 2;
            case LOG -> 1;
            case ALLOW -> 0;
        };
    }

    public ModerationAction strongest(ModerationAction other) {
        return other == null || weight() >= other.weight() ? this : other;
    }
}

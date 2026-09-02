package com.ds.goroute.type;

/**
 * Shape of a rate-plan promotion. The type is descriptive — the conditions that actually decide whether a
 * promotion applies live in its own fields (advance days, stay window, minimum nights), so a partner can
 * build an early-bird deal without the platform hard-coding what "early" means.
 */
public enum PromotionType {
    /** Plain discount inside a date window. */
    BASIC,
    /** Rewards booking far ahead: pair with {@code minAdvanceDays}. */
    EARLY_BIRD,
    /** Fills empty nights close to arrival: pair with {@code maxAdvanceDays}. */
    LAST_MINUTE,
    /** Rewards longer stays: pair with {@code minNights}. */
    LONG_STAY
}

package com.ds.goroute.type;

/**
 * How an expense is divided between the people who share it (epic 05 §2.3). The server always
 * computes each share's final amount from the method and the person's {@code shareInput}.
 */
public enum SplitMethod {
    /** Even shares; the minor-unit remainder goes one unit at a time from the first person. */
    EQUAL,
    /** The amount of each share as sent; they must add up to the expense. */
    EXACT,
    /** {@code shareInput} is a percentage; the percentages add up to 100. */
    PERCENT,
    /** {@code shareInput} is a number of shares, split pro rata. */
    SHARES,
    /** {@code shareInput} is added to (or taken off) the person's share; the rest is split evenly. */
    ADJUSTMENT
}

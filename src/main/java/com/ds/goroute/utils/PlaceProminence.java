package com.ds.goroute.utils;

import java.util.UUID;

/**
 * How well travellers know a place, which decides the places a zoomed-out map keeps and the
 * order of popular searches: how many trips used it and how many check-ins it has (V225), each
 * on a log scale so the hundredth trip adds less than the first. Google's stars count for
 * nothing: they are not trusted. Its review count only orders the places nobody has used yet,
 * at a weight a single trip or check-in outranks ({@link #REVIEW_WEIGHT} times ln(1 + 10^6)
 * is under ln 2).
 *
 * <p>Two places never score the same: a few millionths taken from the id break the tie, so
 * every map tile ranks a given set of places in one order, and a place kept at one zoom is
 * kept at every closer zoom.
 */
public final class PlaceProminence {

    /** Review count's weight: orders unused places without outranking one used once. */
    static final double REVIEW_WEIGHT = 0.04;

    /** Well below the smallest step between two real scores. */
    private static final double TIE_BREAK_SPAN = 1e-6;

    private PlaceProminence() {
    }

    public static double of(UUID id, Integer tripUseCount, Integer checkinCount, Integer reviewCount) {
        return Math.log1p(nonNegative(tripUseCount))
                + Math.log1p(nonNegative(checkinCount))
                + REVIEW_WEIGHT * Math.log1p(nonNegative(reviewCount))
                + tieBreak(id);
    }

    private static int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static double tieBreak(UUID id) {
        if (id == null) {
            return 0.0;
        }
        long bits = id.getLeastSignificantBits() ^ id.getMostSignificantBits();
        return (double) (bits >>> 12) / (1L << 52) * TIE_BREAK_SPAN;
    }
}

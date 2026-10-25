package com.ds.goroute.feed.index;

import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.model.FeedItemRow;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The part of a post's score that does not depend on who is looking: quality Q, engagement E
 * and the community's negative signal N. Stored as feed_items.engagement_score = E x Q x N by
 * FeedIndexJob; freshness and everything about the viewer is applied per request.
 *
 * <p>Formulas: .docs/FEED_RANKING_PLAN_2026-10-22.md §4.3 and §4.8.
 */
public final class FeedItemScoring {

    private FeedItemScoring() {
    }

    /** E x Q x N. */
    public static double staticScore(FeedItemRow row, FeedWeights w) {
        return engagement(row, w) * quality(row, w) * negative(row, w);
    }

    /**
     * Q: how much there is to look at. Photos, text and a catalogue place for a check-in or
     * review; photos, stops, days, description, linked stops, costs and copies for a trip; both
     * times the author's trust (50 is neutral).
     */
    public static double quality(FeedItemRow row, FeedWeights w) {
        double q = 1.0;
        if (row.isTrip()) {
            String p = "quality.trip.";
            q += w.d(p + "memoryEach", 0.06) * Math.min(row.getPhotoCount(), w.i(p + "memoryMax", 10));
            q += w.d(p + "activityEach", 0.05) * Math.min(row.getStopCount(), w.i(p + "activityMax", 12));
            if (row.getDayCount() >= 2) {
                q += w.d(p + "multiDay", 0.15);
            }
            if (row.getTextLen() >= w.i(p + "descriptionChars", 80)) {
                q += w.d(p + "description", 0.1);
            }
            if (row.getStopCount() > 0
                    && (double) row.getLinkedStopCount() / row.getStopCount() >= w.d(p + "linkedStopsShare", 0.5)) {
                q += w.d(p + "linkedStops", 0.1);
            }
            if (row.isHasCost()) {
                q += w.d(p + "hasCost", 0.1);
            }
            int copyCap = Math.max(1, w.i(p + "copyCap", 5));
            q += w.d(p + "copyMax", 0.2) * Math.min(row.getCopyCount(), copyCap) / copyCap;
            if (row.getPhotoCount() == 0 && !row.isHasCover()) {
                q += w.d(p + "noImagePenalty", -0.3);
            }
        } else {
            String group = row.getPlaceGroup();
            if (row.isHasPhoto()) {
                q += value(w, group, "photo", 0.3);
            }
            q += value(w, group, "photoEach", 0.1) * Math.min(row.getPhotoCount(), (int) value(w, group, "photoMax", 3));
            if (row.getTextLen() >= 40) {
                q += value(w, group, "text40", 0.1);
            }
            if (row.getPlaceId() != null) {
                q += value(w, group, "hasPlace", 0.2);
            }
        }
        double trust = 0.9 + row.getAuthorTrust() / w.d("quality.trustDivisor", 500);
        return Math.max(0.3, q * trust);
    }

    /**
     * E: how much people did with the post. Interactions are weighted by how rare they are (a
     * save says more than a like). With few impressions the count speaks (log of the weighted
     * sum); as impressions grow, the smoothed rate per impression takes over, so a post shown a
     * thousand times with ten likes ranks below one shown twenty times with five.
     */
    public static double engagement(FeedItemRow row, FeedWeights w) {
        double weighted = w.d("engagement.like", 1) * row.getLikeCount()
                + w.d("engagement.comment", 4) * row.getCommentCount()
                + w.d("engagement.save", 6) * row.getSaveCount()
                + w.d("engagement.copy", 3) * row.getCopyCount();
        double countScore = 1 + Math.log1p(weighted);

        double priorImpressions = w.d("engagement.priorImpressions", 20);
        double rate = (weighted + w.d("engagement.priorRate", 0.05) * priorImpressions)
                / (row.getImpressionCount() + priorImpressions);
        double rateScore = 1 + w.d("engagement.rateScale", 30) * rate;

        double confidence = row.getImpressionCount()
                / (row.getImpressionCount() + w.d("engagement.minImpressionsForRate", 20));
        double e = (1 - confidence) * countScore + confidence * rateScore;
        return Math.min(w.d("engagement.max", 6), Math.max(0.5, e));
    }

    /** N: hides and reports by anyone push a post down; a report weighs four hides. */
    public static double negative(FeedItemRow row, FeedWeights w) {
        return Math.exp(-w.d("negative.hide", 0.5) * row.getHideCount()
                - w.d("negative.report", 2.0) * row.getReportCount());
    }

    /**
     * Whether a post may be recommended. A trip needs something to look at or follow: a
     * photo, or enough stops (fewer with a description). Check-ins and reviews always may.
     */
    public static boolean recommendOk(FeedItemRow row, FeedWeights w) {
        if (!row.isTrip()) {
            return true;
        }
        return row.getPhotoCount() >= w.i("tripGate.minMemories", 1)
                || row.getStopCount() >= w.i("tripGate.minStops", 3)
                || (row.getStopCount() >= w.i("tripGate.minStopsWithDescription", 2)
                    && row.getTextLen() >= w.i("tripGate.descriptionChars", 80));
    }

    /** A quality weight, overridden per place group under quality.byGroup.{GROUP}. */
    private static double value(FeedWeights w, String group, String key, double fallback) {
        if (group != null) {
            JsonNode override = w.node("quality.byGroup." + group + "." + key);
            if (override != null && override.isNumber()) {
                return override.asDouble();
            }
        }
        return w.d("quality." + key, fallback);
    }
}

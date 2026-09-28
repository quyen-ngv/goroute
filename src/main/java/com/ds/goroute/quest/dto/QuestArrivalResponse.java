package com.ds.goroute.quest.dto;

/**
 * The server's verdict on one location sample (§3.8). The client learns whether it has arrived and
 * how stable the streak is, but the server owns the decision — the geofence is only a hint to open
 * the app. On arrival {@code run} is the run as it now stands, so the app shows the unlocked
 * checkpoint without another round trip; null otherwise.
 */
public record QuestArrivalResponse(
        boolean arrived,
        int stableStreak,
        int requiredStreak,
        Double distanceMeters,
        boolean accuracyAcceptable,
        QuestRunResponse run) {

    public QuestArrivalResponse(boolean arrived, int stableStreak, int requiredStreak,
                                Double distanceMeters, boolean accuracyAcceptable) {
        this(arrived, stableStreak, requiredStreak, distanceMeters, accuracyAcceptable, null);
    }
}

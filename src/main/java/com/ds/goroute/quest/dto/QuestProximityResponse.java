package com.ds.goroute.quest.dto;

/**
 * The hot/cold answer (§3.14.1): a band (HOT, WARM, COOL, COLD) and a trend (CLOSER, FARTHER,
 * SAME; null on the first question). Never a distance: three questions from three places would be
 * enough to find the spot.
 */
public record QuestProximityResponse(String band, String trend) {
}

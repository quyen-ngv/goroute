package com.ds.goroute.dto;

import java.time.LocalDate;

/**
 * Which trips a Feed surface may show, from the FEED config group. Applied by every Feed trip
 * source: public trip search when the Feed asks ({@code recommended} or {@code photosThenStops}),
 * {@code GET /trips/following?feed=true} and the ranked "For you" candidates. Other callers of
 * those queries (Home, Explore, Pengoo) pass null.
 *
 * <p>Part of the public trip ranking cache key, so {@code startedBy} moving to a new day starts
 * a new ranking.
 *
 * @param startedBy           a trip must start on or before this day (a trip under way or over
 *                            passes, one that has not started does not); null turns the rule off.
 *                            Trips without dates pass.
 * @param minActivitiesPerDay a trip needs at least this many activities per day of its length
 *                            (one day when it has no dates); null turns the rule off
 */
public record FeedTripRules(LocalDate startedBy, Double minActivitiesPerDay) {
}

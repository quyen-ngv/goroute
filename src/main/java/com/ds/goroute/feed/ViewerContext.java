package com.ds.goroute.feed;

import com.ds.goroute.feed.model.FeedViewerRows;
import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the ranked feed knows about who is looking. Empty for a guest. The device
 * position is request-scoped and never stored (plan §4.9).
 */
@Value
@Builder(toBuilder = true)
public class ViewerContext {

    public static final ViewerContext GUEST = ViewerContext.builder()
            .following(Map.of())
            .affinity(Map.of())
            .todayProvinces(Set.of())
            .upcomingProvinces(Set.of())
            .itineraryPlaceIds(Set.of())
            .relatedProvinces(Set.of())
            .seen(Map.of())
            .socialProof(Map.of())
            .build();

    UUID viewerId;

    /** Followed user id -> whether they follow back. Closest first. */
    Map<UUID, Boolean> following;
    Map<UUID, Float> affinity;

    /** The trip under way today, when there is one. */
    UUID ongoingTripId;
    String ongoingTripName;
    /** Provinces of the destinations the viewer is at today (or the trip's primary one). */
    Set<String> todayProvinces;
    /** Provinces of trips starting within the upcoming window. */
    Set<String> upcomingProvinces;
    /** Catalogue places on the itineraries of the ongoing and upcoming trips. */
    Set<UUID> itineraryPlaceIds;
    /** Passport wishes, the latest check-ins' provinces and the device's province. */
    Set<String> relatedProvinces;

    Double deviceLat;
    Double deviceLng;
    String deviceProvince;

    /** The viewer turned the trip context off for this trip. */
    boolean contextOff;

    /** "TYPE:id" -> the viewer's impression of it, within the seen window. */
    Map<String, FeedViewerRows.Seen> seen;
    /** "TYPE:id" -> how many of the people the viewer follows engaged with it lately. */
    Map<String, Integer> socialProof;

    public boolean isGuest() {
        return viewerId == null;
    }

    public boolean hasDevice() {
        return deviceLat != null && deviceLng != null;
    }

    /** A trip context the ranking is acting on, which the app names in its context line. */
    public boolean tripContextActive() {
        return !contextOff && ongoingTripId != null
                && (!todayProvinces.isEmpty() || !itineraryPlaceIds.isEmpty());
    }

    /** Any context at all: the context cap of the re-rank only matters then. */
    public boolean anyContext() {
        return tripContextActive() || hasDevice() || (!contextOff && !upcomingProvinces.isEmpty());
    }
}

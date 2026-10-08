package com.ds.goroute.utils;

import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.PlaceRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides whether an incoming place (import, scrape, social candidate, partner create) is
 * one the catalogue already has.
 *
 * <p>Google place id, then CID, are exact. Coordinates alone are not: two shops share a
 * building, so a place inside the radius only counts when its normalized title or address
 * equals the incoming one. Up to {@link #NEARBY_CANDIDATE_LIMIT} nearest places are
 * compared, nearest first, not only the closest one.
 */
public final class PlaceIdentityMatcher {

    /** How many of the nearest places inside the radius a duplicate check compares. */
    public static final int NEARBY_CANDIDATE_LIMIT = 5;

    private PlaceIdentityMatcher() {
    }

    /**
     * The full row of the place the incoming record already is, or empty.
     *
     * @param radiusMeters how far a same-named place may be and still be the same place
     */
    public static Optional<Place> findExisting(PlaceRepository placeRepository,
                                               String googlePlaceId,
                                               String cid,
                                               BigDecimal latitude,
                                               BigDecimal longitude,
                                               String title,
                                               String address,
                                               BigDecimal radiusMeters) {
        if (hasText(googlePlaceId)) {
            Place byPlaceId = placeRepository.findByPlaceId(googlePlaceId);
            if (byPlaceId != null) {
                return Optional.of(byPlaceId);
            }
        }
        if (hasText(cid)) {
            Place byCid = placeRepository.findByCid(cid);
            if (byCid != null) {
                return Optional.of(byCid);
            }
        }
        return findNearbySameIdentityId(placeRepository, latitude, longitude, title, address, radiusMeters)
                .flatMap(placeRepository::findById);
    }

    /** Id of the nearest place inside the radius with the same identity, or empty. */
    public static Optional<UUID> findNearbySameIdentityId(PlaceRepository placeRepository,
                                                          BigDecimal latitude,
                                                          BigDecimal longitude,
                                                          String title,
                                                          String address,
                                                          BigDecimal radiusMeters) {
        if (latitude == null || longitude == null || radiusMeters == null || !hasIdentity(title, address)) {
            return Optional.empty();
        }
        return firstSameIdentity(
                placeRepository.findNearbyIdentityCandidates(latitude, longitude, radiusMeters, NEARBY_CANDIDATE_LIMIT),
                title, address);
    }

    /** The first candidate (callers pass them nearest first) with the same identity. */
    public static Optional<UUID> firstSameIdentity(List<Place> candidates, String title, String address) {
        if (candidates == null || !hasIdentity(title, address)) {
            return Optional.empty();
        }
        for (Place candidate : candidates) {
            if (candidate != null && candidate.getId() != null
                    && sameIdentity(candidate.getTitle(), candidate.getAddress(), title, address)) {
                return Optional.of(candidate.getId());
            }
        }
        return Optional.empty();
    }

    /** Same normalized title, or same normalized address; an empty value never matches. */
    public static boolean sameIdentity(String existingTitle, String existingAddress,
                                       String incomingTitle, String incomingAddress) {
        String normalizedExistingTitle = normalize(existingTitle);
        String normalizedExistingAddress = normalize(existingAddress);
        return !normalizedExistingTitle.isEmpty() && normalizedExistingTitle.equals(normalize(incomingTitle))
                || !normalizedExistingAddress.isEmpty() && normalizedExistingAddress.equals(normalize(incomingAddress));
    }

    /** Lower case, letters and digits only, so punctuation and spacing never decide. */
    public static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static boolean hasIdentity(String title, String address) {
        return !normalize(title).isEmpty() || !normalize(address).isEmpty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

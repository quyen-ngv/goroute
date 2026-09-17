package com.ds.goroute.utils;

import com.ds.goroute.entity.LocationImage;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves which curated tourist area ({@code location_images}) an operational row
 * belongs to.
 *
 * <p>Two signals, deliberately in this order:
 * <ol>
 *   <li>Coordinates, against each area's own {@code coverageRadiusKm}. Objective, and
 *       the only signal that can separate two areas that share a province.</li>
 *   <li>The area name appearing in the row's address or destination text, used only
 *       when coordinates are absent or fall outside every area. An address naming
 *       "Ca Mau" is weaker evidence than a coordinate but far better than nothing.</li>
 * </ol>
 *
 * <p>Name matching is token-based, not substring-based: the accent-stripped form of
 * "Hue" occurs inside "song Nhue" (a Hanoi river), and a substring match would file
 * Hanoi places under Hue. Padding both sides with spaces and comparing whole tokens
 * removes that class of error.
 */
public final class LocationAreaMatcher {

    private static final double EARTH_RADIUS_KM = 6371.0;
    /** Below this, a name is too generic for a token match to mean anything. */
    private static final int MIN_NAME_LENGTH = 3;

    private LocationAreaMatcher() {
    }

    /**
     * Nearest area whose own radius reaches the point.
     *
     * <p>Nearest rather than first match: a place in Hoi An also sits inside Da Nang's
     * radius, and the closer anchor is the right answer.
     */
    public static Optional<LocationImage> matchByCoordinates(
            Collection<LocationImage> areas, BigDecimal latitude, BigDecimal longitude) {
        if (areas == null || latitude == null || longitude == null) {
            return Optional.empty();
        }
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        return areas.stream()
                .filter(area -> area.getLatitude() != null && area.getLongitude() != null)
                .map(area -> new Candidate(area, distanceKm(lat, lng,
                        area.getLatitude().doubleValue(), area.getLongitude().doubleValue())))
                .filter(candidate -> candidate.distanceKm() <= coverageRadiusKm(candidate.area()))
                .min(Comparator.comparingDouble(Candidate::distanceKm))
                .map(Candidate::area);
    }

    /**
     * Area whose name appears as a whole token sequence in any of the supplied texts.
     *
     * <p>Longest name wins, so "Ba Ria Vung Tau" is preferred over "Vung Tau" when both
     * occur; ties fall back to the operator's own priority ordering.
     */
    public static Optional<LocationImage> matchByText(Collection<LocationImage> areas, List<String> texts) {
        if (areas == null || texts == null || texts.isEmpty()) {
            return Optional.empty();
        }
        String haystack = texts.stream()
                .filter(text -> text != null && !text.isBlank())
                .map(LocationAreaMatcher::normalize)
                .filter(text -> !text.isEmpty())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        if (haystack.isEmpty()) {
            return Optional.empty();
        }
        String padded = " " + haystack + " ";
        return areas.stream()
                .map(area -> new NameCandidate(area, normalize(area.getFullAddress())))
                .filter(candidate -> candidate.name().length() >= MIN_NAME_LENGTH)
                .filter(candidate -> padded.contains(" " + candidate.name() + " "))
                .max(Comparator.<NameCandidate>comparingInt(candidate -> candidate.name().length())
                        .thenComparingInt(candidate -> priority(candidate.area())))
                .map(NameCandidate::area);
    }

    /** Coordinates first, then the name fallback. */
    public static Optional<LocationImage> match(
            Collection<LocationImage> areas, BigDecimal latitude, BigDecimal longitude, List<String> texts) {
        Optional<LocationImage> byCoordinates = matchByCoordinates(areas, latitude, longitude);
        return byCoordinates.isPresent() ? byCoordinates : matchByText(areas, texts);
    }

    /**
     * Lowercased, accent-free, single-spaced. Mirrors what
     * {@link DestinationMatchUtils#normalizeKey(String)} does except that spaces are
     * kept, because token boundaries are what make the match safe.
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        withoutMarks = withoutMarks.replace('đ', 'd').replace('Đ', 'D');
        return withoutMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return EARTH_RADIUS_KM * 2 * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    private static double coverageRadiusKm(LocationImage area) {
        return area.getCoverageRadiusKm() == null ? 30.0 : area.getCoverageRadiusKm().doubleValue();
    }

    private static int priority(LocationImage area) {
        return area.getPriority() == null ? 0 : area.getPriority();
    }

    private record Candidate(LocationImage area, double distanceKm) {
    }

    private record NameCandidate(LocationImage area, String name) {
    }
}

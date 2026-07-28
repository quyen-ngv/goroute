package com.ds.goroute.utils;

import java.text.Normalizer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fuzzy destination matching: ignores case, accents, spacing, and supports
 * free-text filters such as "abc phuong xyz ha noi" matching DB value "Ha Noi".
 */
public final class DestinationMatchUtils {

    private static final int MIN_SUBSTRING_LENGTH = 3;
    private static final int MAX_MOJIBAKE_REPAIR_PASSES = 3;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private DestinationMatchUtils() {
    }

    public static int minSubstringLength() {
        return MIN_SUBSTRING_LENGTH;
    }

    public static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutMarks = Normalizer.normalize(repairUtf8Mojibake(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        withoutMarks = withoutMarks.replace('\u0111', 'd').replace('\u0110', 'D');
        return withoutMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Some legacy destination values arrive as UTF-8 bytes decoded one or more
     * times as Windows-1252 (for example, {@code HÃƒâ‚¬ NÃ¡Â»ËœI}). Repair that
     * representation before accent and whitespace normalization so it matches
     * the correctly encoded value instead of silently producing a different key.
     */
    private static String repairUtf8Mojibake(String value) {
        String repaired = value;
        for (int pass = 0; pass < MAX_MOJIBAKE_REPAIR_PASSES; pass++) {
            int currentScore = mojibakeScore(repaired);
            if (currentScore == 0) {
                break;
            }

            String candidate = new String(repaired.getBytes(WINDOWS_1252), StandardCharsets.UTF_8);
            if (candidate.indexOf('\uFFFD') >= 0 || mojibakeScore(candidate) >= currentScore) {
                break;
            }
            repaired = candidate;
        }
        return repaired;
    }

    private static int mojibakeScore(String value) {
        int score = 0;
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '\u00C2', '\u00C3', '\u00C4', '\u00C5', '\u00C6', '\u00E2', '\u00BB', '\u20AC', '\u2122' -> score++;
                default -> {
                    // Not a common UTF-8-as-Windows-1252 artifact.
                }
            }
        }
        return score;
    }

    public static boolean matches(List<String> bookingDestinations, List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        if (bookingDestinations == null || bookingDestinations.isEmpty()) {
            return false;
        }

        Set<String> normalizedBooking = bookingDestinations.stream()
                .filter(Objects::nonNull)
                .map(DestinationMatchUtils::normalizeKey)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.toSet());

        for (String filter : filters) {
            String normalizedFilter = normalizeKey(filter);
            if (normalizedFilter.isEmpty()) {
                continue;
            }
            for (String bookingKey : normalizedBooking) {
                if (keysMatch(normalizedFilter, bookingKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<String> parseFilterValues(List<String> destinations) {
        if (destinations == null) {
            return Collections.emptyList();
        }
        return destinations.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private static boolean keysMatch(String filter, String booking) {
        if (filter.equals(booking)) {
            return true;
        }
        if (booking.length() >= MIN_SUBSTRING_LENGTH && filter.contains(booking)) {
            return true;
        }
        return filter.length() >= MIN_SUBSTRING_LENGTH && booking.contains(filter);
    }
}

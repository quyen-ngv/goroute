package com.ds.goroute.utils;

import java.text.Normalizer;
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

    private DestinationMatchUtils() {
    }

    public static int minSubstringLength() {
        return MIN_SUBSTRING_LENGTH;
    }

    public static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        withoutMarks = withoutMarks.replace('\u0111', 'd').replace('\u0110', 'D');
        return withoutMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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

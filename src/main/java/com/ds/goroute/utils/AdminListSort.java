package com.ds.goroute.utils;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Normalises the {@code sort} / {@code direction} pair an admin list screen sends.
 *
 * <p>A sort key never reaches SQL as text: the controller resolves it against the endpoint's own
 * whitelist and the mapper turns the surviving key into an {@code ORDER BY} through a
 * {@code <choose>} block. An unknown key falls back to the list's natural order rather than
 * failing the request, so a stale bookmark still returns the first page of something sensible.</p>
 */
public final class AdminListSort {

    private AdminListSort() {
    }

    /** The requested key when the endpoint can order by it, otherwise {@code null}. */
    public static String field(String requested, Set<String> allowed) {
        if (requested == null) {
            return null;
        }
        String trimmed = requested.trim();
        return allowed.contains(trimmed) ? trimmed : null;
    }

    /** Descending unless the screen explicitly asked to ascend; lists read newest-first by default. */
    public static boolean descending(String direction) {
        return !"asc".equalsIgnoreCase(direction == null ? null : direction.trim());
    }

    /**
     * Cleans a multi-value column filter arriving as {@code ?status=A,B}.
     *
     * <p>Returns {@code null} — "no filter" — for an absent, empty or all-blank list, so
     * {@code ?status=} narrows nothing instead of matching nothing. Blank entries in the middle
     * are stray separators and are dropped.</p>
     */
    public static List<String> codes(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> cleaned = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** The id-shaped counterpart of {@link #codes}: an empty selection is no filter. */
    public static <T> List<T> ids(List<T> values) {
        if (values == null) {
            return null;
        }
        List<T> cleaned = values.stream().filter(Objects::nonNull).toList();
        return cleaned.isEmpty() ? null : cleaned;
    }
}

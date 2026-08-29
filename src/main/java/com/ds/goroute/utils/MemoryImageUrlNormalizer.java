package com.ds.goroute.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles URLs from the short-lived upload-outcome format that was accidentally
 * persisted as a memory URL by an older Flutter build.
 */
public final class MemoryImageUrlNormalizer {
    private static final Pattern LEGACY_OUTCOME_URL = Pattern.compile(
            "(?:^|[,\\s])url:\\s*(https?://[^,\\s}]+)");

    private MemoryImageUrlNormalizer() {
    }

    public static Optional<String> normalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        if (isHttpUrl(trimmed)) {
            return Optional.of(trimmed);
        }

        Matcher matcher = LEGACY_OUTCOME_URL.matcher(trimmed);
        if (matcher.find() && isHttpUrl(matcher.group(1))) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}

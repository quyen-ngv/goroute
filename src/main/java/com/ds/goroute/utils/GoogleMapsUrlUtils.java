package com.ds.goroute.utils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoogleMapsUrlUtils {

    private static final Pattern PLACE_ID_PARAM = Pattern.compile("[?&]query_place_id=([^&]+)");
    private static final Pattern CHIJ_IN_PATH = Pattern.compile("/(ChIJ[A-Za-z0-9_-]+)");
    private static final Pattern CID_PARAM = Pattern.compile("[?&]cid=(\\d+)");

    private GoogleMapsUrlUtils() {}

    public static String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Google Maps URL is required");
        }
        String trimmed = rawUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            String query = uri.getQuery();
            if (query != null && !query.isBlank()) {
                StringBuilder filtered = new StringBuilder();
                for (String part : query.split("&")) {
                    String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    if (key.startsWith("utm_") || "fbclid".equals(key) || "gclid".equals(key)) {
                        continue;
                    }
                    if (filtered.length() > 0) {
                        filtered.append('&');
                    }
                    filtered.append(part);
                }
                query = filtered.length() > 0 ? filtered.toString() : null;
            }
            String normalized = uri.getScheme() + "://" + host + path;
            if (query != null && !query.isBlank()) {
                normalized += "?" + query;
            }
            return normalized;
        } catch (Exception e) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    public static String hashUrl(String normalizedUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash URL", e);
        }
    }

    public static String extractGooglePlaceId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher paramMatcher = PLACE_ID_PARAM.matcher(url);
        if (paramMatcher.find()) {
            return decode(paramMatcher.group(1));
        }
        Matcher pathMatcher = CHIJ_IN_PATH.matcher(url);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        return null;
    }

    public static String extractCid(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher matcher = CID_PARAM.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}

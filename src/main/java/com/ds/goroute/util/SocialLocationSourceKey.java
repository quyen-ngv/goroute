package com.ds.goroute.util;

import java.net.URI;
import java.util.Locale;

public final class SocialLocationSourceKey {

    private SocialLocationSourceKey() {
    }

    /**
     * Share URLs carry tracking parameters that do not identify different
     * videos. Preserve the path (where TikTok/Instagram store the media id)
     * and discard query/fragment values.
     */
    public static String fromUrl(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("URL must include a host");
            }
            host = canonicalHost(host);
            String path = uri.getRawPath() == null ? "/" : uri.getRawPath();
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return host + path;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid social URL");
        }
    }

    private static String canonicalHost(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        if (value.startsWith("www.")) {
            value = value.substring(4);
        }
        if (value.equals("instagr.am")) {
            return "instagram.com";
        }
        return value;
    }
}

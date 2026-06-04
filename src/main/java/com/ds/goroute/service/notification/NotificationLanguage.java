package com.ds.goroute.service.notification;

import java.util.Locale;
import java.util.Set;

public final class NotificationLanguage {
    public static final String DEFAULT = "en";

    private static final Set<String> SUPPORTED = Set.of("en", "vi", "ja", "ko");

    private NotificationLanguage() {
    }

    public static String normalize(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT;
        }
        String normalized = language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("vi")) {
            return "vi";
        }
        if (normalized.startsWith("ja")) {
            return "ja";
        }
        if (normalized.startsWith("ko")) {
            return "ko";
        }
        return SUPPORTED.contains(normalized) ? normalized : DEFAULT;
    }
}

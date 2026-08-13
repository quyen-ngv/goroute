package com.ds.goroute.service.notification;

import java.util.Locale;
import java.util.Set;

public final class NotificationLanguage {
    public static final String DEFAULT = "en";

    private static final Set<String> SUPPORTED = Set.of(
            "en", "vi", "hi", "ja", "ko", "ru", "th", "zh-TW"
    );

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
        if (normalized.startsWith("hi")) {
            return "hi";
        }
        if (normalized.startsWith("ru")) {
            return "ru";
        }
        if (normalized.startsWith("th")) {
            return "th";
        }
        if (normalized.startsWith("zh")) {
            return "zh-TW";
        }
        return SUPPORTED.contains(normalized) ? normalized : DEFAULT;
    }
}

package com.ds.goroute.service.notification;

import com.ds.goroute.type.TranslationLocale;

import java.util.Set;

public final class NotificationLanguage {
    public static final String DEFAULT = "en";

    private static final Set<String> SUPPORTED = Set.of(
            "en", "vi", "hi", "ja", "ko", "ru", "th", "zh-TW", "zh-CN", "fr", "de", "es"
    );

    private NotificationLanguage() {
    }

    /** The device language as a template language, normalised like Accept-Language; English otherwise. */
    public static String normalize(String language) {
        return TranslationLocale.fromCode(language)
                .map(TranslationLocale::code)
                .filter(SUPPORTED::contains)
                .orElse(DEFAULT);
    }
}

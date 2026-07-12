package com.ds.goroute.type;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum TranslationLocale {
    VI("vi", "Vietnamese"),
    EN("en", "English"),
    TH("th", "Thai"),
    JA("ja", "Japanese"),
    KO("ko", "Korean"),
    ZH_TW("zh-TW", "Traditional Chinese"),
    RU("ru", "Russian"),
    HI("hi", "Hindi"),
    DE("de", "German"),
    ES("es", "Spanish"),
    PT("pt", "Portuguese");

    public static final TranslationLocale DEFAULT = VI;
    public static final TranslationLocale FALLBACK = EN;

    private final String code;
    private final String displayName;

    TranslationLocale(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<TranslationLocale> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if ("zh".equals(normalized) || "zh-cn".equals(normalized) || "zh-tw".equals(normalized)) {
            normalized = "zh-tw";
        }
        String lookup = normalized;
        return Arrays.stream(values())
                .filter(locale -> locale.code.toLowerCase(Locale.ROOT).equals(lookup)
                        || locale.name().replace('_', '-').toLowerCase(Locale.ROOT).equals(lookup))
                .findFirst();
    }
}

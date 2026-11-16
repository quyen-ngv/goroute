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
    ZH_CN("zh-CN", "Simplified Chinese"),
    ZH_TW("zh-TW", "Traditional Chinese"),
    RU("ru", "Russian"),
    HI("hi", "Hindi"),
    FR("fr", "French"),
    DE("de", "German"),
    ES("es", "Spanish"),
    IT("it", "Italian"),
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

    /** The other Chinese script (zh-CN ↔ zh-TW), tried before English when a name is missing; else empty. */
    public Optional<TranslationLocale> sibling() {
        return switch (this) {
            case ZH_CN -> Optional.of(ZH_TW);
            case ZH_TW -> Optional.of(ZH_CN);
            default -> Optional.empty();
        };
    }

    /**
     * The locale a language tag names: zh, zh-CN, zh-Hans*, zh-SG are Simplified Chinese;
     * zh-TW, zh-Hant*, zh-HK, zh-MO Traditional; pt-* Portuguese; any other tag with a region
     * its language part ("fr-CA" → fr). Empty for a language not in the list.
     */
    public static Optional<TranslationLocale> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        Optional<TranslationLocale> exact = Arrays.stream(values())
                .filter(locale -> locale.code.toLowerCase(Locale.ROOT).equals(normalized)
                        || locale.name().replace('_', '-').toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        int dash = normalized.indexOf('-');
        String primary = dash < 0 ? normalized : normalized.substring(0, dash);
        if ("zh".equals(primary)) {
            return Optional.of(isTraditionalChinese(normalized) ? ZH_TW : ZH_CN);
        }
        if (dash < 0) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(locale -> locale.code.equals(primary))
                .findFirst();
    }

    private static boolean isTraditionalChinese(String tag) {
        return tag.startsWith("zh-hant") || tag.startsWith("zh-tw")
                || tag.startsWith("zh-hk") || tag.startsWith("zh-mo");
    }
}

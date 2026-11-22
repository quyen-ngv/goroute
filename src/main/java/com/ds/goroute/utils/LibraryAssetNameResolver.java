package com.ds.goroute.utils;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.entity.LibraryAsset;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Library asset names by request language, with the same fallbacks as {@link FoodNameResolver}. */
public final class LibraryAssetNameResolver {

    private LibraryAssetNameResolver() {
    }

    /** Name in the request language, then English, then Vietnamese; {@code null} when none is set. */
    public static String resolveName(LibraryAsset asset) {
        if (asset == null) {
            return null;
        }
        String lang = AcceptLanguageFilter.currentCode().toLowerCase(Locale.ROOT);
        String fromLang = switch (lang) {
            case "vi" -> asset.getNameVi();
            case "ja" -> asset.getNameJa();
            case "ko" -> asset.getNameKo();
            default -> asset.getNameEn();
        };
        if (hasText(fromLang)) {
            return fromLang.trim();
        }
        if (hasText(asset.getNameEn())) {
            return asset.getNameEn().trim();
        }
        return hasText(asset.getNameVi()) ? asset.getNameVi().trim() : null;
    }

    public static Map<String, String> allNames(LibraryAsset asset) {
        Map<String, String> names = new LinkedHashMap<>();
        putIfText(names, "vi", asset.getNameVi());
        putIfText(names, "en", asset.getNameEn());
        putIfText(names, "ja", asset.getNameJa());
        putIfText(names, "ko", asset.getNameKo());
        return names;
    }

    private static void putIfText(Map<String, String> names, String lang, String value) {
        if (hasText(value)) {
            names.put(lang, value.trim());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

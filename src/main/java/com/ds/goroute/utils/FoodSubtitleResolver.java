package com.ds.goroute.utils;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.entity.Food;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class FoodSubtitleResolver {

    private FoodSubtitleResolver() {
    }

    public static String resolveSubtitle(Food food) {
        if (food == null) {
            return null;
        }
        String lang = AcceptLanguageFilter.currentCode().toLowerCase(Locale.ROOT);
        String fromLang = switch (lang) {
            case "vi" -> food.getSubtitleVi();
            case "ja" -> food.getSubtitleJa();
            case "ko" -> food.getSubtitleKo();
            case "zh-tw" -> food.getSubtitleJa();
            default -> food.getSubtitleEn();
        };
        if (fromLang != null && !fromLang.isBlank()) {
            return fromLang.trim();
        }
        if (food.getSubtitleEn() != null && !food.getSubtitleEn().isBlank()) {
            return food.getSubtitleEn().trim();
        }
        if (food.getSubtitleVi() != null && !food.getSubtitleVi().isBlank()) {
            return food.getSubtitleVi().trim();
        }
        return null;
    }

    public static Map<String, String> allSubtitles(Food food) {
        Map<String, String> subtitles = new LinkedHashMap<>();
        if (food.getSubtitleVi() != null && !food.getSubtitleVi().isBlank()) {
            subtitles.put("vi", food.getSubtitleVi());
        }
        if (food.getSubtitleEn() != null && !food.getSubtitleEn().isBlank()) {
            subtitles.put("en", food.getSubtitleEn());
        }
        if (food.getSubtitleJa() != null && !food.getSubtitleJa().isBlank()) {
            subtitles.put("ja", food.getSubtitleJa());
        }
        if (food.getSubtitleKo() != null && !food.getSubtitleKo().isBlank()) {
            subtitles.put("ko", food.getSubtitleKo());
        }
        return subtitles;
    }
}

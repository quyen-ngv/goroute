package com.ds.goroute.utils;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.entity.Food;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the hero tagline (short slogan shown under the food name on the detail hero,
 * e.g. "Crispy outside. Full of flavor inside.") honoring the Accept-Language header.
 */
public final class FoodTaglineResolver {

    private FoodTaglineResolver() {
    }

    public static String resolveTagline(Food food) {
        if (food == null) {
            return null;
        }
        String lang = AcceptLanguageFilter.currentCode().toLowerCase(Locale.ROOT);
        String fromLang = switch (lang) {
            case "vi" -> food.getHeroTaglineVi();
            case "ja" -> food.getHeroTaglineJa();
            case "ko" -> food.getHeroTaglineKo();
            case "zh-tw" -> food.getHeroTaglineJa();
            default -> food.getHeroTaglineEn();
        };
        if (fromLang != null && !fromLang.isBlank()) {
            return fromLang.trim();
        }
        if (food.getHeroTaglineEn() != null && !food.getHeroTaglineEn().isBlank()) {
            return food.getHeroTaglineEn().trim();
        }
        if (food.getHeroTaglineVi() != null && !food.getHeroTaglineVi().isBlank()) {
            return food.getHeroTaglineVi().trim();
        }
        return null;
    }

    public static Map<String, String> allTaglines(Food food) {
        Map<String, String> taglines = new LinkedHashMap<>();
        if (food.getHeroTaglineVi() != null && !food.getHeroTaglineVi().isBlank()) {
            taglines.put("vi", food.getHeroTaglineVi());
        }
        if (food.getHeroTaglineEn() != null && !food.getHeroTaglineEn().isBlank()) {
            taglines.put("en", food.getHeroTaglineEn());
        }
        if (food.getHeroTaglineJa() != null && !food.getHeroTaglineJa().isBlank()) {
            taglines.put("ja", food.getHeroTaglineJa());
        }
        if (food.getHeroTaglineKo() != null && !food.getHeroTaglineKo().isBlank()) {
            taglines.put("ko", food.getHeroTaglineKo());
        }
        return taglines;
    }
}

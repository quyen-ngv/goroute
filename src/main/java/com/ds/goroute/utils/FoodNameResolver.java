package com.ds.goroute.utils;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.entity.Food;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class FoodNameResolver {

    private FoodNameResolver() {
    }

    public static String resolveName(Food food) {
        if (food == null) {
            return "";
        }
        String lang = AcceptLanguageFilter.currentCode().toLowerCase(Locale.ROOT);
        String fromLang = switch (lang) {
            case "vi" -> food.getNameVi();
            case "ja" -> food.getNameJa();
            case "ko" -> food.getNameKo();
            case "zh-tw" -> food.getNameJa();
            default -> food.getNameEn();
        };
        if (fromLang != null && !fromLang.isBlank()) {
            return fromLang;
        }
        if (food.getNameEn() != null && !food.getNameEn().isBlank()) {
            return food.getNameEn();
        }
        return food.getNameVi() != null ? food.getNameVi() : "";
    }

    public static Map<String, String> allNames(Food food) {
        Map<String, String> names = new LinkedHashMap<>();
        if (food.getNameVi() != null) {
            names.put("vi", food.getNameVi());
        }
        if (food.getNameEn() != null) {
            names.put("en", food.getNameEn());
        }
        if (food.getNameJa() != null) {
            names.put("ja", food.getNameJa());
        }
        if (food.getNameKo() != null) {
            names.put("ko", food.getNameKo());
        }
        return names;
    }
}

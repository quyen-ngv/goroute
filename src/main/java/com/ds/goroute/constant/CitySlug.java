package com.ds.goroute.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Getter
public enum CitySlug {
    HANOI("hanoi", "Hà Nội"),
    HCMC("hcmc", "TP. Hồ Chí Minh"),
    DANANG("danang", "Đà Nẵng"),
    HOIAN("hoian", "Hội An"),
    HUE("hue", "Huế"),
    NHATRANG("nhatrang", "Nha Trang"),
    PHUQUOC("phuquoc", "Phú Quốc");

    private final String slug;
    private final String displayName;

    CitySlug(String slug, String displayName) {
        this.slug = slug;
        this.displayName = displayName;
    }

    public static Optional<CitySlug> fromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(c -> c.slug.equals(normalized))
                .findFirst();
    }

    public String toJsonbFilter() {
        return "[\"" + slug + "\"]";
    }
}

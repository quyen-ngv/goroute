package com.ds.goroute.utils;

import com.ds.goroute.constant.CitySlug;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;

import java.util.List;
import java.util.Locale;
import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CitySlugResolver {

    private static final Pattern CITY_SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private static final List<AliasEntry> ALIASES = List.of(
            new AliasEntry(CitySlug.HANOI, List.of("hanoi", "hni", "hanoivietnam", "thanhphohanoi")),
            new AliasEntry(CitySlug.HCMC, List.of("hcmc", "hcm", "hochiminh", "saigon", "tphcm", "thanhphohochiminh")),
            new AliasEntry(CitySlug.DANANG, List.of("danang", "thanhphodanang")),
            new AliasEntry(CitySlug.HOIAN, List.of("hoian", "phocohoian")),
            new AliasEntry(CitySlug.HUE, List.of("hue", "thanhphohue", "cothue")),
            new AliasEntry(CitySlug.NHATRANG, List.of("nhatrang", "thanhphonhatrang")),
            new AliasEntry(CitySlug.PHUQUOC, List.of("phuquoc", "daophuquoc"))
    );

    private CitySlugResolver() {
    }

    public static CitySlug resolveRequired(String citySlug) {
        return fromSlug(citySlug)
                .orElseThrow(() -> new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid citySlug: " + citySlug));
    }

    public static String normalizeRequired(String citySlug) {
        if (citySlug == null || citySlug.isBlank()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "citySlug is required");
        }
        String aliasKey = DestinationMatchUtils.normalizeKey(citySlug);
        Optional<CitySlug> direct = CitySlug.fromSlug(aliasKey);
        if (direct.isPresent()) {
            return direct.get().getSlug();
        }
        for (AliasEntry entry : ALIASES) {
            for (String alias : entry.aliases()) {
                if (aliasKey.equals(alias)) {
                    return entry.city().getSlug();
                }
            }
        }
        String withoutAccents = Normalizer.normalize(citySlug.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D');
        String normalized = withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank() || !CITY_SLUG_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid citySlug: " + citySlug);
        }
        return normalized;
    }

    public static String toJsonbFilter(String citySlug) {
        return "[\"" + normalizeRequired(citySlug) + "\"]";
    }

    public static Optional<CitySlug> fromSlug(String citySlug) {
        return CitySlug.fromSlug(citySlug);
    }

    public static Optional<CitySlug> resolveFromDestination(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = DestinationMatchUtils.normalizeKey(text);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        Optional<CitySlug> direct = CitySlug.fromSlug(normalized);
        if (direct.isPresent()) {
            return direct;
        }
        for (AliasEntry entry : ALIASES) {
            for (String alias : entry.aliases()) {
                if (normalized.equals(alias)
                        || (alias.length() >= 3 && (normalized.contains(alias) || alias.contains(normalized)))) {
                    return Optional.of(entry.city());
                }
            }
        }
        if (text.contains(",")) {
            for (String part : text.split(",")) {
                Optional<CitySlug> fromPart = resolveFromDestination(part.trim());
                if (fromPart.isPresent()) {
                    return fromPart;
                }
            }
        }
        return Optional.empty();
    }

    public static String displayName(CitySlug citySlug) {
        return citySlug.getDisplayName();
    }

    public static String displayName(String citySlug) {
        String normalized = normalizeRequired(citySlug);
        Optional<CitySlug> known = CitySlug.fromSlug(normalized);
        if (known.isPresent()) {
            return known.get().getDisplayName();
        }
        String[] parts = normalized.split("-");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return label.length() == 0 ? normalized : label.toString();
    }

    private record AliasEntry(CitySlug city, List<String> aliases) {
    }
}

package com.ds.goroute.utils;

/**
 * WMO 4677 weather-code lookups: English label, Vietnamese label and an FE icon token.
 *
 * <p>Codes for snow and freezing precipitation are kept even though they never fire in
 * Vietnam, so the catalog stays a complete mapping of what Open-Meteo can return.
 */
public final class WeatherCodeCatalog {

    private WeatherCodeCatalog() {
    }

    public static String description(Integer weatherCode) {
        if (weatherCode == null) {
            return "Unknown";
        }
        return switch (weatherCode) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snowfall";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown";
        };
    }

    public static String descriptionVi(Integer weatherCode) {
        if (weatherCode == null) {
            return "Không xác định";
        }
        return switch (weatherCode) {
            case 0 -> "Trời quang";
            case 1 -> "Ít mây";
            case 2 -> "Có mây";
            case 3 -> "Nhiều mây";
            case 45, 48 -> "Sương mù";
            case 51 -> "Mưa phùn nhẹ";
            case 53 -> "Mưa phùn";
            case 55 -> "Mưa phùn nặng hạt";
            case 56, 57 -> "Mưa phùn băng giá";
            case 61 -> "Mưa nhỏ";
            case 63 -> "Mưa vừa";
            case 65 -> "Mưa to";
            case 66, 67 -> "Mưa băng giá";
            case 71, 73, 75 -> "Tuyết rơi";
            case 77 -> "Hạt tuyết";
            case 80 -> "Mưa rào nhẹ";
            case 81 -> "Mưa rào";
            case 82 -> "Mưa rào rất to";
            case 85, 86 -> "Mưa tuyết";
            case 95 -> "Dông";
            case 96, 99 -> "Dông kèm mưa đá";
            default -> "Không xác định";
        };
    }

    /**
     * Icon token for the FE. Codes that look different by day and night get a
     * {@code -day} / {@code -night} suffix; the rest are time-independent.
     *
     * @param isDay {@code null} is treated as daytime
     */
    public static String icon(Integer weatherCode, Boolean isDay) {
        String suffix = Boolean.FALSE.equals(isDay) ? "-night" : "-day";
        if (weatherCode == null) {
            return "unknown";
        }
        return switch (weatherCode) {
            case 0 -> "clear" + suffix;
            case 1 -> "mostly-clear" + suffix;
            case 2 -> "partly-cloudy" + suffix;
            case 3 -> "overcast";
            case 45, 48 -> "fog";
            case 51, 53, 55, 56, 57 -> "drizzle";
            case 61, 63, 65, 66, 67 -> "rain";
            case 71, 73, 75, 77, 85, 86 -> "snow";
            case 80, 81, 82 -> "rain-showers" + suffix;
            case 95, 96, 99 -> "thunderstorm";
            default -> "unknown";
        };
    }

    /** True for codes that mean precipitation is actually falling. */
    public static boolean isPrecipitating(Integer weatherCode) {
        if (weatherCode == null) {
            return false;
        }
        return weatherCode >= 51;
    }
}

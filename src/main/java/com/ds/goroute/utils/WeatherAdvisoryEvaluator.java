package com.ds.goroute.utils;

import com.ds.goroute.dto.response.CityAirQualityResponse;
import com.ds.goroute.dto.response.DailyForecastResponse;
import com.ds.goroute.dto.response.WeatherAlertResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw forecast numbers into travel advisories using Vietnamese conventions.
 *
 * <p>Bands mirror the ones NCHMF uses on the national bulletin so the wording matches
 * what a Vietnamese traveller already hears on the news: rainfall classified per 24h
 * (mưa to 51-100mm, mưa rất to above 100mm), heat on the apparent maximum (nắng nóng
 * from 35C, gay gắt from 37C, đặc biệt gay gắt above 39C) and wind on the Beaufort
 * scale (cấp 6 gió mạnh, cấp 8 the threshold Vietnam calls a storm).
 *
 * <p>These are derived hints, never a substitute for an official NCHMF bulletin.
 */
public final class WeatherAdvisoryEvaluator {

    private static final String LEVEL_INFO = "INFO";
    private static final String LEVEL_WARNING = "WARNING";
    private static final String LEVEL_DANGER = "DANGER";

    private static final String[] COMPASS_POINTS = {
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };

    /** Upper bound in km/h of each Beaufort force, index = force. */
    private static final double[] BEAUFORT_UPPER_BOUNDS = {
        1, 5, 11, 19, 28, 38, 49, 61, 74, 88, 102, 117
    };

    private WeatherAdvisoryEvaluator() {
    }

    public static String windDirectionLabel(Integer degrees) {
        if (degrees == null) {
            return null;
        }
        int normalized = ((degrees % 360) + 360) % 360;
        return COMPASS_POINTS[(int) Math.round(normalized / 22.5) % COMPASS_POINTS.length];
    }

    public static Integer beaufortForce(Double windSpeedKmh) {
        if (windSpeedKmh == null) {
            return null;
        }
        for (int force = 0; force < BEAUFORT_UPPER_BOUNDS.length; force++) {
            if (windSpeedKmh <= BEAUFORT_UPPER_BOUNDS[force]) {
                return force;
            }
        }
        return 12;
    }

    /** Stable token for the US AQI band, used by the FE for colouring. */
    public static String aqiCategoryCode(Integer usAqi) {
        if (usAqi == null) {
            return null;
        }
        if (usAqi <= 50) {
            return "good";
        }
        if (usAqi <= 100) {
            return "moderate";
        }
        if (usAqi <= 150) {
            return "sensitive";
        }
        if (usAqi <= 200) {
            return "unhealthy";
        }
        if (usAqi <= 300) {
            return "very_unhealthy";
        }
        return "hazardous";
    }

    public static String aqiCategory(String categoryCode) {
        if (categoryCode == null) {
            return null;
        }
        return switch (categoryCode) {
            case "good" -> "Good";
            case "moderate" -> "Moderate";
            case "sensitive" -> "Unhealthy for sensitive groups";
            case "unhealthy" -> "Unhealthy";
            case "very_unhealthy" -> "Very unhealthy";
            case "hazardous" -> "Hazardous";
            default -> null;
        };
    }

    public static String aqiCategoryVi(String categoryCode) {
        if (categoryCode == null) {
            return null;
        }
        return switch (categoryCode) {
            case "good" -> "Tốt";
            case "moderate" -> "Trung bình";
            case "sensitive" -> "Kém, nhóm nhạy cảm nên hạn chế ra ngoài";
            case "unhealthy" -> "Xấu";
            case "very_unhealthy" -> "Rất xấu";
            case "hazardous" -> "Nguy hại";
            default -> null;
        };
    }

    public static String aqiAdvice(String categoryCode) {
        if (categoryCode == null) {
            return null;
        }
        return switch (categoryCode) {
            case "good" -> "Air quality is good. Outdoor sightseeing is comfortable.";
            case "moderate" -> "Acceptable for most travellers; sensitive people may want shorter outdoor stints.";
            case "sensitive" -> "Sensitive groups should limit long outdoor activity.";
            case "unhealthy" -> "Everyone should cut back on outdoor exertion and consider a mask.";
            case "very_unhealthy" -> "Avoid outdoor activity; stay indoors where you can.";
            case "hazardous" -> "Health emergency. Stay indoors.";
            default -> null;
        };
    }

    public static String aqiAdviceVi(String categoryCode) {
        if (categoryCode == null) {
            return null;
        }
        return switch (categoryCode) {
            case "good" -> "Chất lượng không khí tốt, thoải mái tham quan ngoài trời.";
            case "moderate" -> "Chấp nhận được với hầu hết mọi người; ai nhạy cảm nên rút ngắn thời gian ngoài trời.";
            case "sensitive" -> "Nhóm nhạy cảm (trẻ em, người già, người bệnh hô hấp) nên hạn chế hoạt động ngoài trời.";
            case "unhealthy" -> "Mọi người nên giảm vận động ngoài trời và cân nhắc đeo khẩu trang lọc bụi mịn.";
            case "very_unhealthy" -> "Hạn chế tối đa ra ngoài, nên ở trong nhà.";
            case "hazardous" -> "Mức nguy hại cho sức khỏe. Không nên ra ngoài.";
            default -> null;
        };
    }

    /**
     * Build the advisory list for a location.
     *
     * @param currentWindGustsKmh current gust, may be null
     * @param currentWeatherCode  WMO code of the current conditions, may be null
     * @param today               the first day of the outlook, may be null
     * @param airQuality          current air quality, may be null
     * @return advisories ordered most severe first; empty when nothing is noteworthy
     */
    public static List<WeatherAlertResponse> evaluate(
        Double currentWindGustsKmh,
        Integer currentWeatherCode,
        DailyForecastResponse today,
        CityAirQualityResponse airQuality
    ) {
        List<WeatherAlertResponse> alerts = new ArrayList<>();

        addWindAlert(alerts, currentWindGustsKmh, today);
        addRainAlert(alerts, today);
        addHeatAlert(alerts, today);
        addThunderstormAlert(alerts, currentWeatherCode);
        addAirQualityAlert(alerts, airQuality);
        addUvAlert(alerts, today);

        alerts.sort((left, right) -> Integer.compare(severityRank(right.getLevel()), severityRank(left.getLevel())));
        return alerts;
    }

    private static void addWindAlert(
        List<WeatherAlertResponse> alerts,
        Double currentWindGustsKmh,
        DailyForecastResponse today
    ) {
        Double gust = maxOf(currentWindGustsKmh, today == null ? null : today.getWindGustsMaxKmh());
        if (gust == null) {
            return;
        }
        long rounded = Math.round(gust);
        Integer force = beaufortForce(gust);
        if (gust >= 89) {
            alerts.add(WeatherAlertResponse.builder()
                .code("STORM_FORCE_WIND")
                .level(LEVEL_DANGER)
                .title("Storm-force wind")
                .titleVi("Gió bão mạnh")
                .description("Gusts up to " + rounded + " km/h (Beaufort " + force
                    + "). Travel by road and sea is dangerous.")
                .descriptionVi("Gió giật tới " + rounded + " km/h (cấp " + force
                    + "). Rất nguy hiểm khi di chuyển đường bộ và đường biển.")
                .build());
        } else if (gust >= 62) {
            alerts.add(WeatherAlertResponse.builder()
                .code("STORM_FORCE_WIND")
                .level(LEVEL_WARNING)
                .title("Gale-force wind")
                .titleVi("Gió mạnh cấp bão")
                .description("Gusts up to " + rounded + " km/h (Beaufort " + force
                    + "). Boat trips and coastal activities are likely to be cancelled.")
                .descriptionVi("Gió giật tới " + rounded + " km/h (cấp " + force
                    + "). Các tour tàu thuyền và hoạt động ven biển nhiều khả năng bị hủy.")
                .build());
        } else if (gust >= 50) {
            alerts.add(WeatherAlertResponse.builder()
                .code("STRONG_WIND")
                .level(LEVEL_INFO)
                .title("Strong wind")
                .titleVi("Gió mạnh")
                .description("Gusts up to " + rounded + " km/h (Beaufort " + force + ").")
                .descriptionVi("Gió giật tới " + rounded + " km/h (cấp " + force + ").")
                .build());
        }
    }

    private static void addRainAlert(List<WeatherAlertResponse> alerts, DailyForecastResponse today) {
        if (today == null || today.getPrecipitationSumMm() == null) {
            return;
        }
        double rain = today.getPrecipitationSumMm();
        long rounded = Math.round(rain);
        if (rain > 100) {
            alerts.add(WeatherAlertResponse.builder()
                .code("VERY_HEAVY_RAIN")
                .level(LEVEL_DANGER)
                .title("Very heavy rain")
                .titleVi("Mưa rất to")
                .description(rounded + " mm expected today. Flash flooding and urban flooding are likely.")
                .descriptionVi("Dự kiến " + rounded + " mm trong hôm nay. Nguy cơ ngập úng và lũ quét.")
                .build());
        } else if (rain > 50) {
            alerts.add(WeatherAlertResponse.builder()
                .code("HEAVY_RAIN")
                .level(LEVEL_WARNING)
                .title("Heavy rain")
                .titleVi("Mưa to")
                .description(rounded + " mm expected today. Expect localised flooding on city streets.")
                .descriptionVi("Dự kiến " + rounded + " mm trong hôm nay. Có thể ngập cục bộ trên đường phố.")
                .build());
        }
    }

    private static void addHeatAlert(List<WeatherAlertResponse> alerts, DailyForecastResponse today) {
        if (today == null || today.getApparentTemperatureMaxC() == null) {
            return;
        }
        double feelsLike = today.getApparentTemperatureMaxC();
        long rounded = Math.round(feelsLike);
        if (feelsLike >= 39) {
            alerts.add(WeatherAlertResponse.builder()
                .code("EXTREME_HEAT")
                .level(LEVEL_DANGER)
                .title("Extreme heat")
                .titleVi("Nắng nóng đặc biệt gay gắt")
                .description("Feels like " + rounded + "C. Avoid outdoor sightseeing between 11:00 and 15:00.")
                .descriptionVi("Cảm giác như " + rounded + " độ C. Tránh tham quan ngoài trời từ 11h đến 15h.")
                .build());
        } else if (feelsLike >= 37) {
            alerts.add(WeatherAlertResponse.builder()
                .code("SEVERE_HEAT")
                .level(LEVEL_WARNING)
                .title("Severe heat")
                .titleVi("Nắng nóng gay gắt")
                .description("Feels like " + rounded + "C. Drink water often and seek shade.")
                .descriptionVi("Cảm giác như " + rounded + " độ C. Uống nhiều nước và tìm chỗ râm mát.")
                .build());
        } else if (feelsLike >= 35) {
            alerts.add(WeatherAlertResponse.builder()
                .code("HEAT")
                .level(LEVEL_INFO)
                .title("Hot")
                .titleVi("Nắng nóng")
                .description("Feels like " + rounded + "C.")
                .descriptionVi("Cảm giác như " + rounded + " độ C.")
                .build());
        }
    }

    private static void addThunderstormAlert(List<WeatherAlertResponse> alerts, Integer currentWeatherCode) {
        if (currentWeatherCode == null) {
            return;
        }
        if (currentWeatherCode == 96 || currentWeatherCode == 99) {
            alerts.add(WeatherAlertResponse.builder()
                .code("THUNDERSTORM")
                .level(LEVEL_DANGER)
                .title("Thunderstorm with hail")
                .titleVi("Dông kèm mưa đá")
                .description("Take shelter indoors and stay away from trees and metal structures.")
                .descriptionVi("Hãy vào nơi trú ẩn, tránh xa cây cao và các kết cấu kim loại.")
                .build());
        } else if (currentWeatherCode == 95) {
            alerts.add(WeatherAlertResponse.builder()
                .code("THUNDERSTORM")
                .level(LEVEL_WARNING)
                .title("Thunderstorm")
                .titleVi("Có dông")
                .description("Lightning risk. Postpone open-air activities.")
                .descriptionVi("Nguy cơ sét đánh. Nên hoãn các hoạt động ngoài trời.")
                .build());
        }
    }

    private static void addAirQualityAlert(List<WeatherAlertResponse> alerts, CityAirQualityResponse airQuality) {
        if (airQuality == null || airQuality.getUsAqi() == null) {
            return;
        }
        int usAqi = airQuality.getUsAqi();
        if (usAqi <= 100) {
            return;
        }
        String level = usAqi > 200 ? LEVEL_DANGER : usAqi > 150 ? LEVEL_WARNING : LEVEL_INFO;
        alerts.add(WeatherAlertResponse.builder()
            .code("POOR_AIR_QUALITY")
            .level(level)
            .title("Poor air quality")
            .titleVi("Chất lượng không khí kém")
            .description("US AQI " + usAqi + ". " + airQuality.getAdvice())
            .descriptionVi("Chỉ số AQI (Mỹ) " + usAqi + ". " + airQuality.getAdviceVi())
            .build());
    }

    private static void addUvAlert(List<WeatherAlertResponse> alerts, DailyForecastResponse today) {
        if (today == null || today.getUvIndexMax() == null || today.getUvIndexMax() < 8) {
            return;
        }
        double uv = today.getUvIndexMax();
        long rounded = Math.round(uv);
        String level = uv >= 11 ? LEVEL_WARNING : LEVEL_INFO;
        alerts.add(WeatherAlertResponse.builder()
            .code("HIGH_UV")
            .level(level)
            .title("High UV index")
            .titleVi("Chỉ số UV cao")
            .description("UV index peaks at " + rounded + ". Use sunscreen and cover up around midday.")
            .descriptionVi("Chỉ số UV cao nhất " + rounded
                + ". Nên bôi kem chống nắng và che chắn vào buổi trưa.")
            .build());
    }

    private static Double maxOf(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    private static int severityRank(String level) {
        return switch (level) {
            case LEVEL_DANGER -> 3;
            case LEVEL_WARNING -> 2;
            default -> 1;
        };
    }
}

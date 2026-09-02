package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A travel advisory derived from the forecast, not an official bulletin.
 *
 * <p>Thresholds follow the Vietnamese meteorological conventions used by NCHMF
 * (rainfall bands per 24h, heat bands on apparent maximum, Beaufort wind force),
 * so the wording lines up with what a Vietnamese user sees on the news.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherAlertResponse {
    /** Stable token, e.g. STORM_FORCE_WIND, VERY_HEAVY_RAIN, EXTREME_HEAT, THUNDERSTORM, POOR_AIR_QUALITY, HIGH_UV. */
    private String code;
    /** INFO | WARNING | DANGER */
    private String level;
    private String title;
    private String titleVi;
    private String description;
    private String descriptionVi;
}

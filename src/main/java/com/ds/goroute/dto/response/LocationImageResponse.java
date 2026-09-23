package com.ds.goroute.dto.response;

import com.ds.goroute.dto.LocationDescriptionSection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationImageResponse {
    private UUID id;
    private String fullAddress;
    private String citySlug;
    private String provinceCode;
    private String slogan;
    private List<LocationDescriptionSection> description;
    private String imageUrl;
    private String avatarUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer priority;

    /** Radius in km within which a place/tour/hotel counts as part of this area. */
    private BigDecimal coverageRadiusKm;
    private List<String> wardCodes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /**
     * Live weather, air quality and advisories for this location.
     *
     * <p>Only populated when the caller asks for it, and left {@code null} when the
     * location has no coordinates or the weather provider is unavailable.
     */
    private CityWeatherResponse weather;
}

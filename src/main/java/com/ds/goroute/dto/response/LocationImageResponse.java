package com.ds.goroute.dto.response;

import com.ds.goroute.dto.LocationDescriptionSection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import com.ds.goroute.enums.LocationImageKind;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationImageResponse {
    private UUID id;
    /** COUNTRY or REGION. */
    private LocationImageKind kind;
    /** ISO 3166-1 alpha-2 of the country this entry is, or is in. */
    private String countryCode;
    /** The country's name in the request's language. */
    private String countryName;
    /** A region's country entry; null on a country. */
    private UUID parentId;
    /** The country's main currency (ISO 4217). */
    private String currencyCode;
    /** The name to show, in the request's language; {@code fullAddress} when there is no translation. */
    private String name;
    /** Translated names by locale code. Only on the single-entry response, for the admin editor. */
    private java.util.Map<String, String> nameTranslations;
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

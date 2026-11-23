package com.ds.goroute.dto.request;

import com.ds.goroute.enums.LocationImageKind;
import com.ds.goroute.dto.LocationDescriptionSection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLocationImageRequest {
    @NotBlank(message = "Full address is required")
    private String fullAddress;

    @Size(max = 255, message = "Slogan must not exceed 255 characters")
    private String slogan;

    @Valid
    private List<LocationDescriptionSection> description;

    @NotBlank(message = "Image URL is required")
    private String imageUrl;
    private String avatarUrl;
    @Size(max = 10)
    private String provinceCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    
    @NotNull(message = "Priority is required")
    private Integer priority;

    /** Radius in km within which a place/tour/hotel counts as part of this area. */
    @DecimalMin(value = "0.1", message = "Coverage radius must be greater than 0")
    @DecimalMax(value = "500", message = "Coverage radius must not exceed 500 km")
    private BigDecimal coverageRadiusKm;

    /** Official ward codes making up the area; a place in any of them belongs here before any radius is consulted. */
    private List<String> wardCodes;

    /** COUNTRY or REGION; a new entry is a REGION when omitted. */
    private LocationImageKind kind;

    /** ISO 3166-1 alpha-2; Vietnam when omitted on create. */
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be two letters")
    private String countryCode;

    /** ISO 4217; a region takes its country's when omitted. */
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency code must be three letters")
    private String currencyCode;

    /** Names in other languages by locale code ("en", "ja", "zh-CN"); replaces the stored set when present. */
    private java.util.Map<@Size(max = 10) String, @Size(max = 255) String> nameTranslations;
}

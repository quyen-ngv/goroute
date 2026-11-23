package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.time.LocalDateTime;
import com.ds.goroute.enums.LocationImageKind;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationImage {
    private UUID id;
    /** COUNTRY or REGION (V296). */
    private LocationImageKind kind;
    /** ISO 3166-1 alpha-2 of the country this row is, or is in. */
    private String countryCode;
    /** A region's country row; null on a country. */
    private UUID parentId;
    /** The country's main currency (ISO 4217); null when unknown. */
    private String currencyCode;
    private String fullAddress;
    private String normalizedAddress;
    private String slogan;
    private String description;
    private String imageUrl;
    private String avatarUrl;
    private String citySlug;
    /** Optional legacy province code; Passport scope now anchors directly to this image. */
    private String provinceCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer priority;
    /** Radius in km within which an operational row is considered part of this tourist area. */
    private BigDecimal coverageRadiusKm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void normalizeAddress() {
        if (fullAddress != null) {
            this.normalizedAddress = normalizeVietnamese(fullAddress.toLowerCase());
        }
    }

    private String normalizeVietnamese(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

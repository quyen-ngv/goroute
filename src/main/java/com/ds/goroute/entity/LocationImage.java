package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationImage {
    private UUID id;
    private String fullAddress;
    private String normalizedAddress;
    private String imageUrl;
    private String avatarUrl;
    private String citySlug;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer priority;
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

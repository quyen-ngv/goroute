package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A circle (home, work) whose part of a recorded route nobody but its owner sees, not even
 * other members of the trip ({@code user_privacy_zones}, TR-23).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyZone {
    private UUID id;
    private UUID userId;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer radiusM;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long dataVersion;
}

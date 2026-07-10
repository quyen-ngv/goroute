package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationImageResponse {
    private UUID id;
    private String fullAddress;
    private String citySlug;
    private String imageUrl;
    private String avatarUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

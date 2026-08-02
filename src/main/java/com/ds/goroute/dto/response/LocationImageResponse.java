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
    private String slogan;
    private List<LocationDescriptionSection> description;
    private String imageUrl;
    private String avatarUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

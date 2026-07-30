package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class RoomTypeResponse {
    private UUID id;
    private UUID hotelId;
    private String code;
    private String name;
    private String description;
    private Integer maxAdults;
    private Integer maxChildren;
    private Integer maxOccupancy;
    private List<Map<String, Object>> bedConfig;
    private List<String> amenities;
    private List<String> images;
    private BigDecimal roomSizeSqm;
    private Integer totalUnits;
    private String status;
    private String disabledReason;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

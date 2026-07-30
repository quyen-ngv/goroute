package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomType {
    private UUID id;
    private UUID hotelId;
    private String code;
    private String name;
    private String description;
    private Integer maxAdults;
    private Integer maxChildren;
    private Integer maxOccupancy;
    private String bedConfig;
    private String amenities;
    private String images;
    private BigDecimal roomSizeSqm;
    private Integer totalUnits;
    private String status;
    private String disabledReason;
    private Long dataVersion;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

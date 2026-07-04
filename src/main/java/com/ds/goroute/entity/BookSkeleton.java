package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSkeleton {
    private UUID id;
    private String skeletonKey;
    private Integer version;
    private String name;
    private String pageType;
    private Integer photoCount;
    private Integer canvasWidth;
    private Integer canvasHeight;
    private String slotDefs;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

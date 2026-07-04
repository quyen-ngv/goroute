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
public class BookPage {
    private UUID id;
    private UUID bookId;
    private UUID activityId;
    private String pageType;
    private Integer pageOrder;
    private String skeletonId;
    private String skeletonKey;
    private Integer skeletonVersion;
    private UUID templateId;
    private String slots;
    private String layoutMode;
    private LocalDateTime layoutEditedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

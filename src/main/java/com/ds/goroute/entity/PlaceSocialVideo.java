package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceSocialVideo {
    private UUID id;
    private UUID placeId;
    private UUID socialJobId;
    private String sourceUrl;
    private String canonicalUrl;
    private String platform;
    private String title;
    private String thumbnailUrl;
    private String creatorName;
    private String videoSummary;
    private String videoUsefulSummary;
    private String generalGuidance;
    private String placeRecap;
    private String usefulInfo;
    private String visitGuidance;
    private String evidenceSources;
    private String evidenceText;
    private BigDecimal confidence;
    private String language;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

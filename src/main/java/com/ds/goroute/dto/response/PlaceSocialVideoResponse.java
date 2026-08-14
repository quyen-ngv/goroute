package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceSocialVideoResponse {
    private UUID id;
    private String sourceUrl;
    private String platform;
    private String title;
    private String thumbnailUrl;
    private String creatorName;
    private String videoSummary;
    private String videoUsefulSummary;
    private List<String> generalGuidance;
    private String placeRecap;
    private List<String> usefulInfo;
    private List<String> visitGuidance;
    private List<String> evidenceSources;
    private List<String> evidenceText;
    private BigDecimal confidence;
    private String language;
    private LocalDateTime createdAt;
}

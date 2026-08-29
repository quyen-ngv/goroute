package com.ds.goroute.entity;

import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Per-image moderation outcome, kept for appeals and for threshold tuning (MOD-04). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageModerationResult {
    private UUID id;
    private String objectKey;
    private String checksum;
    private UUID userId;
    private String entryPoint;
    private ModerationAction decision;
    private ModerationCategory category;
    private BigDecimal confidence;
    private String provider;
    private String rawScores;
    private LocalDateTime createdAt;
}

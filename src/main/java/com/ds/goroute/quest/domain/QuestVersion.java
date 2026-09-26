package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One immutable snapshot of everything a player sees and a reviewer reads (§6.2, §6.2.1). Every
 * edit mints a new row; a run pins a version id, so a pinned row is never rewritten.
 *
 * <p>{@code amenityTags}, {@code playableMonths} and {@code playableHours} are stored as JSON
 * text (the driver casts to JSONB); {@code checkpoints} is populated only by the graph load.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestVersion {

    private UUID id;
    private UUID questId;
    private Integer version;
    private Integer contentRevision;
    private String changeKind;
    private String title;
    private String summary;
    private String description;
    private UUID coverMediaId;
    private Integer difficulty;
    private Integer estimatedMinutes;
    private Integer distanceMeters;
    /** JSON array of amenity tag codes. */
    private String amenityTags;
    private String safetyNotes;
    private String provinceCode;
    private String wardCode;
    private String contentLanguage;
    private Integer priceStars;
    private Integer rewardStars;
    /** JSON array of month numbers (1-12), or null for all year. */
    private String playableMonths;
    /** JSON object of playable hour windows, or null for any time. */
    private String playableHours;
    private Integer runExpiryHours;
    private UUID createdBy;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<QuestCheckpoint> checkpoints = new ArrayList<>();
}

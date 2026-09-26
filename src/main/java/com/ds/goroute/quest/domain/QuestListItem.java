package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A flat projection of a published quest for discovery (§6.3): the quest joined to its published
 * version, plus two checkpoint counts. Deliberately has no coordinates, answers or hints — it feeds
 * the public DTOs only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestListItem {

    private UUID questId;
    private String origin;
    private UUID publishedVersionId;
    private String title;
    private String summary;
    private String description;
    private UUID coverMediaId;
    private Integer difficulty;
    private Integer estimatedMinutes;
    private Integer distanceMeters;
    private Integer priceStars;
    private Integer rewardStars;
    private String provinceCode;
    private String wardCode;
    private String safetyNotes;
    private String contentLanguage;
    /** JSON array text; the service parses it. */
    private String amenityTags;
    private String playableMonths;
    private Integer runExpiryHours;
    private Integer checkpointCount;
    private Integer requiredCheckinCount;
}

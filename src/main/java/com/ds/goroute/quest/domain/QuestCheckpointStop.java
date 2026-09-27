package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A storytelling point inside a checkpoint (§3.14.2). Its coordinates are where the listener
 * stands. Sent to the app only once the checkpoint is unlocked.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestCheckpointStop {

    private UUID id;
    private UUID checkpointId;
    private Integer sortOrder;
    private String name;
    private String category;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer radiusM;
    private String story;
    /** JSON array of photo URLs; "[]" when none. */
    private String imageUrls;
    private String audioUrl;
    private Integer audioSeconds;
    private UUID placeId;
    private LocalDateTime createdAt;
}

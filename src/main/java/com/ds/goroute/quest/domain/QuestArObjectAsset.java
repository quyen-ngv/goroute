package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A 3D object in the AR library (§3.15). The console uploads it; creators only pick it. The GLB is
 * the source and what Android shows; iOS shows the USDZ made from it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestArObjectAsset {

    private UUID id;
    private String name;
    private String description;
    private String glbUrl;
    private Long glbBytes;
    private String usdzUrl;
    private Long usdzBytes;
    private String thumbnailUrl;
    private BigDecimal heightM;
    /** JSON array of {name, seconds}, in the order the clips play on the USDZ timeline. */
    private String clips;
    private Integer triangles;
    private boolean canWander;
    /** JSON array of tag strings. */
    private String tags;
    private boolean active;
    private UUID createdBy;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

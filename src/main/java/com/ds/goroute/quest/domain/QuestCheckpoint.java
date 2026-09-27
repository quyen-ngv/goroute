package com.ds.goroute.quest.domain;

import com.ds.goroute.type.QuestCaptureSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A stop on the route (§6.2). Carries its own coordinates (D2); {@code placeId} is optional and,
 * after publish, the runtime reads {@code radiusM} / {@code unlockGeometryWkt} off the checkpoint,
 * never {@code places}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestCheckpoint {

    private UUID id;
    private UUID questVersionId;
    private Integer sortOrder;
    private String name;
    /** Trip activity category id (V188); null when the creator set none. */
    private String category;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer radiusM;
    /** WKT of the polygon frozen at publish (read/written as text; the mapper casts to geometry). */
    private String unlockGeometryWkt;
    private UUID placeId;
    private String locationKey;
    private String story;
    private String captureSource;
    private BigDecimal captureAccuracyMeters;
    private LocalDateTime capturedAt;
    private boolean requiresCheckin;
    /** JSON array of photo URLs that help a player find the spot (V187); "[]" when none. */
    private String imageUrls;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<QuestQuestion> questions = new ArrayList<>();

    public QuestCaptureSource capture() {
        return QuestCaptureSource.valueOf(captureSource);
    }
}

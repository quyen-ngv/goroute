package com.ds.goroute.quest.domain;

import com.ds.goroute.type.QuestCaptureSource;
import com.ds.goroute.type.QuestCompletionMode;
import com.ds.goroute.type.QuestFindMode;
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
    /** PIN or AREA (§3.14.1); see {@link QuestFindMode}. */
    private String findMode;
    /** AREA only: radius of the search circle, 50–1000 m and larger than {@code radiusM}. */
    private Integer searchRadiusM;
    /** AREA only: centre of the search circle, offset from the real spot, computed on save. */
    private BigDecimal searchCenterLat;
    private BigDecimal searchCenterLng;
    private boolean hotColdEnabled;
    /** TASK, ARRIVE, STOPS (§3.14) or AR_OBJECT (§3.15); see {@link QuestCompletionMode}. */
    private String completionMode;
    private Integer minStops;
    private String storyAudioUrl;
    private Integer storyAudioSeconds;
    /** AR_OBJECT only: the object, as the JSON of {@link QuestArObject}; null otherwise. */
    private String arObject;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<QuestQuestion> questions = new ArrayList<>();

    @Builder.Default
    private List<QuestCheckpointClue> clues = new ArrayList<>();

    @Builder.Default
    private List<QuestCheckpointStop> stops = new ArrayList<>();

    public QuestCaptureSource capture() {
        return QuestCaptureSource.valueOf(captureSource);
    }

    public QuestFindMode find() {
        return QuestFindMode.of(findMode);
    }

    public QuestCompletionMode completion() {
        return QuestCompletionMode.of(completionMode);
    }

    public boolean isArea() {
        return find() == QuestFindMode.AREA;
    }
}

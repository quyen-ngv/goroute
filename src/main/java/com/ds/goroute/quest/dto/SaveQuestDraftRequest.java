package com.ds.goroute.quest.dto;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The whole draft, PUT back on every save with {@code expectedVersion} (partner-listing v2
 * mechanics): the server replaces the draft version's content wholesale, so "step done" is always
 * derived from the data, never a stored flag. Every free-text leaf is {@link ModeratedText}, so
 * editing goes through the same filter as creating (D7).
 */
@Data
public class SaveQuestDraftRequest {

    private long expectedVersion;

    @ModeratedText(contentType = ModeratedContentType.QUEST, label = "title")
    private String title;
    @ModeratedText(contentType = ModeratedContentType.QUEST, label = "summary")
    private String summary;
    @ModeratedText(contentType = ModeratedContentType.QUEST, label = "description")
    private String description;
    @ModeratedText(contentType = ModeratedContentType.QUEST, label = "safetyNotes")
    private String safetyNotes;

    private UUID coverMediaId;
    private String coverUrl;
    private Integer difficulty;
    private Integer estimatedMinutes;
    private Integer distanceMeters;
    private List<String> amenityTags = new ArrayList<>();
    /** location_images ids the quest is tagged to (the cities), chosen first in the create flow. */
    private List<String> cityImageIds = new ArrayList<>();
    private String provinceCode;
    private String wardCode;
    private String contentLanguage;
    private Integer priceStars;
    private Integer rewardStars;
    private List<Integer> playableMonths;
    private Map<String, Object> playableHours;
    private Integer runExpiryHours;

    private List<CheckpointInput> checkpoints = new ArrayList<>();

    @Data
    public static class CheckpointInput {
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "checkpoint.name")
        private String name;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "checkpoint.story")
        private String story;

        /** Trip activity category id; see QuestBuilderServiceImpl.CHECKPOINT_CATEGORIES. */
        private String category;

        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer radiusM;
        private UUID placeId;
        private String captureSource;
        private BigDecimal captureAccuracyMeters;
        private LocalDateTime capturedAt;
        private boolean requiresCheckin;
        /** Photos that help a player find the spot, as URLs from the shared upload endpoint. */
        private List<String> imageUrls = new ArrayList<>();
        private List<QuestionInput> questions = new ArrayList<>();

        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "checkpoint.note")
        private List<String> notes = new ArrayList<>();

        /** PIN (default) or AREA, §3.14.1. */
        private String findMode;
        /** AREA only: 50–1000 m, larger than {@code radiusM}. The centre is computed by the server. */
        private Integer searchRadiusM;
        private boolean hotColdEnabled;
        /** TASK (default), ARRIVE or STOPS, §3.14. */
        private String completionMode;
        /** STOPS only: 1 to the number of storytelling points. */
        private Integer minStops;
        /** The creator's recording of {@code story}, a URL from the audio upload endpoint. */
        private String storyAudioUrl;
        private Integer storyAudioSeconds;
        /** AREA only: finding clues, tier 1 first; at most three, REVEAL last. */
        private List<ClueInput> clues = new ArrayList<>();
        /** Storytelling points inside this checkpoint (§3.14.2). */
        private List<StopInput> stops = new ArrayList<>();
        /** AR_OBJECT only (§3.15): the object to find. Ignored for any other completion mode. */
        private ArObjectInput arObject;
    }

    /** §3.15. The same shape as {@link com.ds.goroute.quest.domain.QuestArObject}, as sent by a client. */
    @Data
    public static class ArObjectInput {
        private UUID assetId;
        /** FIXED (default) or WANDER. */
        private String behavior;
        /** APPROX (default) or IMAGE. */
        private String anchorMode;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer headingDeg;
        private Integer spawnRadiusM;
        private Integer wanderRadiusM;
        private BigDecimal scale;
        private List<MarkerInput> markers = new ArrayList<>();
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "arObject.title")
        private String title;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "arObject.description")
        private String description;
        private List<String> imageUrls = new ArrayList<>();
        private String audioUrl;
        private Integer audioSeconds;
    }

    /** A landmark photo; {@code offset} is where the object stands in the landmark's frame, in metres. */
    @Data
    public static class MarkerInput {
        private String imageUrl;
        private BigDecimal widthM;
        private Vec3Input offset;
        private BigDecimal yawDeg;
    }

    @Data
    public static class Vec3Input {
        private BigDecimal x;
        private BigDecimal y;
        private BigDecimal z;
    }

    @Data
    public static class ClueInput {
        private Integer tier;
        /** TEXT, PHOTO or REVEAL. */
        private String kind;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "clue.text")
        private String text;
        private String imageUrl;
        private Integer costStars;
    }

    @Data
    public static class StopInput {
        /** Accepted for the console's round trip but not reused: every saved version mints new ids. */
        private UUID stopId;
        private Integer sortOrder;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "stop.name")
        private String name;
        private String category;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer radiusM;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "stop.story")
        private String story;
        private List<String> imageUrls = new ArrayList<>();
        private String audioUrl;
        private Integer audioSeconds;
        private UUID placeId;
    }

    @Data
    public static class QuestionInput {
        private boolean required = true;
        private boolean bonus;
        private String type;

        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "question.prompt")
        private String prompt;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "question.answer")
        private String answerPlain;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "question.answerVariants")
        private List<String> answerVariants = new ArrayList<>();
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "question.hint1")
        private String hintTier1;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "question.hint2")
        private String hintTier2;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "question.hint3")
        private String hintTier3;

        private UUID imageMediaId;
        private BigDecimal numberTolerance;
        private Integer bonusStars;
        private List<ChoiceInput> choices = new ArrayList<>();
    }

    @Data
    public static class ChoiceInput {
        /** Stable id; kept across saves so answers stored by id survive a shuffle. Null → new. */
        private UUID id;
        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "choice.content")
        private String content;
        private boolean correct;
    }
}

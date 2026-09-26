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
    private Integer difficulty;
    private Integer estimatedMinutes;
    private Integer distanceMeters;
    private List<String> amenityTags = new ArrayList<>();
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

        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer radiusM;
        private UUID placeId;
        private String captureSource;
        private BigDecimal captureAccuracyMeters;
        private LocalDateTime capturedAt;
        private boolean requiresCheckin;
        private List<QuestionInput> questions = new ArrayList<>();

        @ModeratedText(contentType = ModeratedContentType.QUEST_CHECKPOINT, label = "checkpoint.note")
        private List<String> notes = new ArrayList<>();
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

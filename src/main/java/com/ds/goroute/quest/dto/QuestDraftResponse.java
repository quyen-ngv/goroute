package com.ds.goroute.quest.dto;

import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCreatorNote;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestQuestionChoice;
import com.ds.goroute.quest.domain.QuestVersion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The full draft as its creator (or a reviewer) sees it — answers, coordinates and hints included.
 * This is deliberately NOT a public shape: it is only ever returned from creator/admin endpoints.
 * The public shapes live in {@link QuestPublicDetailResponse} / {@link QuestPublicSummaryResponse}.
 */
public record QuestDraftResponse(
        UUID id,
        UUID creatorId,
        String origin,
        String status,
        long dataVersion,
        UUID draftVersionId,
        UUID publishedVersionId,
        boolean pendingChangeReview,
        VersionView version) {

    public record VersionView(
            UUID id,
            int version,
            int contentRevision,
            String changeKind,
            String title,
            String summary,
            String description,
            UUID coverMediaId,
            Integer difficulty,
            Integer estimatedMinutes,
            Integer distanceMeters,
            List<String> amenityTags,
            String safetyNotes,
            String provinceCode,
            String wardCode,
            String contentLanguage,
            int priceStars,
            int rewardStars,
            List<CheckpointView> checkpoints) {
    }

    public record CheckpointView(
            UUID id,
            int sortOrder,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radiusM,
            UUID placeId,
            String story,
            String captureSource,
            boolean requiresCheckin,
            List<QuestionView> questions,
            List<String> notes) {
    }

    public record QuestionView(
            UUID id,
            int sortOrder,
            boolean required,
            boolean bonus,
            String type,
            String prompt,
            UUID imageMediaId,
            String answerPlain,
            BigDecimal numberTolerance,
            String hintTier1,
            String hintTier2,
            String hintTier3,
            int bonusStars,
            List<ChoiceView> choices) {
    }

    public record ChoiceView(UUID id, int sortOrder, String content, boolean correct) {
    }

    public static QuestDraftResponse of(Quest quest, QuestVersion version,
                                        Map<UUID, List<QuestCreatorNote>> notesByCheckpoint,
                                        List<String> amenityTags) {
        List<CheckpointView> checkpoints = version.getCheckpoints().stream()
                .map(cp -> checkpointView(cp, notesByCheckpoint.getOrDefault(cp.getId(), List.of())))
                .toList();
        VersionView versionView = new VersionView(
                version.getId(), n(version.getVersion()), n(version.getContentRevision()),
                version.getChangeKind(), version.getTitle(), version.getSummary(), version.getDescription(),
                version.getCoverMediaId(), version.getDifficulty(), version.getEstimatedMinutes(),
                version.getDistanceMeters(), amenityTags, version.getSafetyNotes(), version.getProvinceCode(),
                version.getWardCode(), version.getContentLanguage(), n(version.getPriceStars()),
                n(version.getRewardStars()), checkpoints);
        return new QuestDraftResponse(quest.getId(), quest.getCreatorId(), quest.getOrigin(),
                quest.getStatus(), quest.getDataVersion() == null ? 0 : quest.getDataVersion(),
                quest.getDraftVersionId(), quest.getPublishedVersionId(), quest.isPendingChangeReview(),
                versionView);
    }

    private static CheckpointView checkpointView(QuestCheckpoint cp, List<QuestCreatorNote> notes) {
        List<QuestionView> questions = cp.getQuestions().stream()
                .map(QuestDraftResponse::questionView)
                .toList();
        return new CheckpointView(cp.getId(), n(cp.getSortOrder()), cp.getName(), cp.getLatitude(),
                cp.getLongitude(), cp.getRadiusM(), cp.getPlaceId(), cp.getStory(), cp.getCaptureSource(),
                cp.isRequiresCheckin(), questions, notes.stream().map(QuestCreatorNote::getNote).toList());
    }

    private static QuestionView questionView(QuestQuestion q) {
        List<ChoiceView> choices = q.getChoices().stream()
                .map(c -> new ChoiceView(c.getId(), n(c.getSortOrder()), c.getContent(), c.isCorrect()))
                .toList();
        return new QuestionView(q.getId(), n(q.getSortOrder()), q.isRequired(), q.isBonus(), q.getType(),
                q.getPrompt(), q.getImageMediaId(), q.getAnswerPlain(), q.getNumberTolerance(),
                q.getHintTier1(), q.getHintTier2(), q.getHintTier3(), n(q.getBonusStars()), choices);
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }
}

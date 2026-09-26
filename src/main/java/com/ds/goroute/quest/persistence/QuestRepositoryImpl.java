package com.ds.goroute.quest.persistence;

import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCreatorNote;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestQuestionChoice;
import com.ds.goroute.quest.domain.QuestVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class QuestRepositoryImpl implements QuestRepository {

    private final QuestMapper mapper;

    @Override
    public Optional<QuestCreatorProfile> findCreatorByUser(UUID userId) {
        return Optional.ofNullable(mapper.findCreatorByUser(userId));
    }

    @Override
    public Optional<QuestCreatorProfile> findCreatorById(UUID id) {
        return Optional.ofNullable(mapper.findCreatorById(id));
    }

    @Override
    public void insertCreator(QuestCreatorProfile creator) {
        mapper.insertCreator(creator);
    }

    @Override
    public boolean updateCreatorTerms(UUID id, long expectedVersion, LocalDateTime acceptedAt,
                                      String termsVersion, LocalDateTime updatedAt) {
        return mapper.updateCreatorTerms(id, expectedVersion, acceptedAt, termsVersion, updatedAt) == 1;
    }

    @Override
    public void updateCreatorSubmission(UUID id, LocalDateTime lastSubmittedAt) {
        mapper.updateCreatorSubmission(id, lastSubmittedAt);
    }

    @Override
    public void updateCreatorDenied(UUID id, int deniedStreak, LocalDateTime blockedUntil) {
        mapper.updateCreatorDenied(id, deniedStreak, blockedUntil);
    }

    @Override
    public void resetCreatorDenied(UUID id) {
        mapper.resetCreatorDenied(id);
    }

    @Override
    public void updateCreatorStatus(UUID id, String status) {
        mapper.updateCreatorStatus(id, status);
    }

    @Override
    public void insertQuest(Quest quest) {
        mapper.insertQuest(quest);
    }

    @Override
    public Optional<Quest> findQuestById(UUID id) {
        return Optional.ofNullable(mapper.findQuestById(id));
    }

    @Override
    public List<Quest> findQuestsByCreator(UUID creatorId, String status, int limit, int offset) {
        return mapper.findQuestsByCreator(creatorId, status, limit, offset);
    }

    @Override
    public long countQuestsByCreator(UUID creatorId, String status) {
        return mapper.countQuestsByCreator(creatorId, status);
    }

    @Override
    public long countQuestsByCreatorAndStatuses(UUID creatorId, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return 0;
        }
        return mapper.countQuestsByCreatorAndStatuses(creatorId, statuses);
    }

    @Override
    public boolean updateDraftPointer(UUID id, long expectedVersion, UUID draftVersionId, LocalDateTime updatedAt) {
        return mapper.updateDraftPointer(id, expectedVersion, draftVersionId, updatedAt) == 1;
    }

    @Override
    public boolean updateStatus(UUID id, long expectedVersion, String status, String pausedBy, LocalDateTime updatedAt) {
        return mapper.updateStatus(id, expectedVersion, status, pausedBy, updatedAt) == 1;
    }

    @Override
    public List<com.ds.goroute.quest.domain.QuestListItem> findPublicQuests(String provinceCode, String language,
                                                                            int limit, int offset) {
        return mapper.findPublicQuests(provinceCode, language, limit, offset);
    }

    @Override
    public long countPublicQuests(String provinceCode, String language) {
        return mapper.countPublicQuests(provinceCode, language);
    }

    @Override
    public Optional<com.ds.goroute.quest.domain.QuestListItem> findPublicQuestDetail(UUID id) {
        return Optional.ofNullable(mapper.findPublicQuestDetail(id));
    }

    @Override
    public boolean updatePublish(UUID id, long expectedVersion, UUID publishedVersionId, boolean selfApproved,
                                 LocalDateTime updatedAt) {
        return mapper.updatePublish(id, expectedVersion, publishedVersionId, selfApproved, updatedAt) == 1;
    }

    @Override
    public List<Quest> findForReview(List<String> statuses, int limit, int offset) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return mapper.findForReview(statuses, limit, offset);
    }

    @Override
    public long countForReview(List<String> statuses) {
        return statuses == null || statuses.isEmpty() ? 0 : mapper.countForReview(statuses);
    }

    @Override
    public void insertReviewDecision(com.ds.goroute.quest.domain.QuestReviewDecisionRow decision) {
        mapper.insertReviewDecision(decision);
    }

    @Override
    public void insertReviewComment(com.ds.goroute.quest.domain.QuestReviewComment comment) {
        mapper.insertReviewComment(comment);
    }

    @Override
    public List<com.ds.goroute.quest.domain.QuestReviewDecisionRow> findDecisionsByQuest(UUID questId) {
        return mapper.findDecisionsByQuest(questId);
    }

    @Override
    public List<com.ds.goroute.quest.domain.QuestReviewComment> findCommentsByQuest(UUID questId) {
        return mapper.findCommentsByQuest(questId);
    }

    @Override
    public void insertVersion(QuestVersion version) {
        mapper.insertVersion(version);
    }

    @Override
    public Optional<QuestVersion> findVersionById(UUID id) {
        return Optional.ofNullable(mapper.findVersionById(id));
    }

    @Override
    public Optional<QuestVersion> loadVersionGraph(UUID versionId) {
        QuestVersion version = mapper.findVersionById(versionId);
        if (version == null) {
            return Optional.empty();
        }
        List<QuestCheckpoint> checkpoints = mapper.findCheckpointsByVersion(versionId);
        version.setCheckpoints(checkpoints);
        if (checkpoints.isEmpty()) {
            return Optional.of(version);
        }
        List<UUID> checkpointIds = checkpoints.stream().map(QuestCheckpoint::getId).toList();
        List<QuestQuestion> questions = mapper.findQuestionsByCheckpointIds(checkpointIds);
        if (!questions.isEmpty()) {
            List<UUID> questionIds = questions.stream().map(QuestQuestion::getId).toList();
            List<QuestQuestionChoice> choices = mapper.findChoicesByQuestionIds(questionIds);
            Map<UUID, List<QuestQuestionChoice>> choicesByQuestion = choices.stream()
                    .collect(Collectors.groupingBy(QuestQuestionChoice::getQuestionId));
            questions.forEach(q -> q.setChoices(choicesByQuestion.getOrDefault(q.getId(), List.of())));
        }
        Map<UUID, List<QuestQuestion>> questionsByCheckpoint = questions.stream()
                .collect(Collectors.groupingBy(QuestQuestion::getCheckpointId));
        checkpoints.forEach(c -> c.setQuestions(questionsByCheckpoint.getOrDefault(c.getId(), List.of())));
        return Optional.of(version);
    }

    @Override
    public List<QuestCreatorNote> findNotesByCheckpoints(List<UUID> checkpointIds) {
        if (checkpointIds == null || checkpointIds.isEmpty()) {
            return List.of();
        }
        return mapper.findNotesByCheckpointIds(checkpointIds);
    }

    @Override
    public int countCheckpoints(UUID versionId) {
        return versionId == null ? 0 : mapper.countCheckpointsByVersion(versionId);
    }

    @Override
    public void updateVersionContent(QuestVersion version) {
        mapper.updateVersionContent(version);
    }

    @Override
    public void insertCheckpoint(QuestCheckpoint checkpoint) {
        mapper.insertCheckpoint(checkpoint);
    }

    @Override
    public void insertQuestion(QuestQuestion question) {
        mapper.insertQuestion(question);
    }

    @Override
    public void insertChoice(QuestQuestionChoice choice) {
        mapper.insertChoice(choice);
    }

    @Override
    public void insertNote(QuestCreatorNote note) {
        mapper.insertNote(note);
    }

    @Override
    public void clearVersionContent(UUID versionId) {
        // Deepest table first: no physical FKs to cascade for us.
        mapper.deleteChoicesByVersion(versionId);
        mapper.deleteQuestionsByVersion(versionId);
        mapper.deleteNotesByVersion(versionId);
        mapper.deleteCheckpointsByVersion(versionId);
    }
}

package com.ds.goroute.quest.persistence;

import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCreatorNote;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestVersion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Thin persistence facade over {@link QuestMapper}; assembles the version graph in memory. */
public interface QuestRepository {

    Optional<QuestCreatorProfile> findCreatorByUser(UUID userId);

    Optional<QuestCreatorProfile> findCreatorById(UUID id);

    void insertCreator(QuestCreatorProfile creator);

    boolean updateCreatorTerms(UUID id, long expectedVersion, LocalDateTime acceptedAt,
                               String termsVersion, LocalDateTime updatedAt);

    void updateCreatorSubmission(UUID id, LocalDateTime lastSubmittedAt);

    void updateCreatorDenied(UUID id, int deniedStreak, LocalDateTime blockedUntil);

    void resetCreatorDenied(UUID id);

    void updateCreatorStatus(UUID id, String status);

    void insertQuest(Quest quest);

    Optional<Quest> findQuestById(UUID id);

    List<Quest> findQuestsByCreator(UUID creatorId, String status, int limit, int offset);

    long countQuestsByCreator(UUID creatorId, String status);

    long countQuestsByCreatorAndStatuses(UUID creatorId, List<String> statuses);

    boolean updateDraftPointer(UUID id, long expectedVersion, UUID draftVersionId, LocalDateTime updatedAt);

    boolean updateStatus(UUID id, long expectedVersion, String status, String pausedBy, LocalDateTime updatedAt);

    boolean updatePublish(UUID id, long expectedVersion, UUID publishedVersionId, boolean selfApproved,
                          LocalDateTime updatedAt);

    List<Quest> findForReview(List<String> statuses, int limit, int offset);

    long countForReview(List<String> statuses);

    void insertReviewDecision(com.ds.goroute.quest.domain.QuestReviewDecisionRow decision);

    void insertReviewComment(com.ds.goroute.quest.domain.QuestReviewComment comment);

    List<com.ds.goroute.quest.domain.QuestReviewDecisionRow> findDecisionsByQuest(UUID questId);

    List<com.ds.goroute.quest.domain.QuestReviewComment> findCommentsByQuest(UUID questId);

    List<com.ds.goroute.quest.domain.QuestListItem> findPublicQuests(String provinceCode, String language,
                                                                     int limit, int offset);

    long countPublicQuests(String provinceCode, String language);

    Optional<com.ds.goroute.quest.domain.QuestListItem> findPublicQuestDetail(UUID id);

    void insertVersion(QuestVersion version);

    Optional<QuestVersion> findVersionById(UUID id);

    /** The version plus its checkpoints, each with its questions, each with its choices. */
    Optional<QuestVersion> loadVersionGraph(UUID versionId);

    List<QuestCreatorNote> findNotesByCheckpoints(List<UUID> checkpointIds);

    int countCheckpoints(UUID versionId);

    void updateVersionContent(QuestVersion version);

    void insertCheckpoint(QuestCheckpoint checkpoint);

    void insertQuestion(QuestQuestion question);

    void insertChoice(com.ds.goroute.quest.domain.QuestQuestionChoice choice);

    void insertNote(QuestCreatorNote note);

    /** Wipes the whole content graph of a version, deepest table first. */
    void clearVersionContent(UUID versionId);
}

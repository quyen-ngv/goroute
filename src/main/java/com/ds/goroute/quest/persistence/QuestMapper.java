package com.ds.goroute.quest.persistence;

import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCreatorNote;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestQuestionChoice;
import com.ds.goroute.quest.domain.QuestVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for the quest builder. Single-table reads assembled in the repository, per the
 * project's SQL convention; the whole draft graph is rewritten on each save, which is safe for a
 * draft (≤ {@code MAX_CHECKPOINTS} rows) and is what "step done is derived from data" needs.
 */
@Mapper
public interface QuestMapper {

    // --- creator profile ---------------------------------------------------------------
    QuestCreatorProfile findCreatorByUser(@Param("userId") UUID userId);

    QuestCreatorProfile findCreatorById(@Param("id") UUID id);

    void insertCreator(QuestCreatorProfile creator);

    int updateCreatorTerms(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
                           @Param("termsAcceptedAt") LocalDateTime termsAcceptedAt,
                           @Param("termsVersion") String termsVersion,
                           @Param("updatedAt") LocalDateTime updatedAt);

    int updateCreatorSubmission(@Param("id") UUID id, @Param("lastSubmittedAt") LocalDateTime lastSubmittedAt);

    int updateCreatorDenied(@Param("id") UUID id, @Param("deniedStreak") int deniedStreak,
                            @Param("blockedUntil") LocalDateTime blockedUntil);

    int resetCreatorDenied(@Param("id") UUID id);

    int updateCreatorStatus(@Param("id") UUID id, @Param("status") String status);

    // --- quest root --------------------------------------------------------------------
    void insertQuest(Quest quest);

    Quest findQuestById(@Param("id") UUID id);

    List<Quest> findQuestsByCreator(@Param("creatorId") UUID creatorId, @Param("status") String status,
                                    @Param("limit") int limit, @Param("offset") int offset);

    long countQuestsByCreator(@Param("creatorId") UUID creatorId, @Param("status") String status);

    long countQuestsByCreatorAndStatuses(@Param("creatorId") UUID creatorId,
                                         @Param("statuses") List<String> statuses);

    int updateDraftPointer(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
                           @Param("draftVersionId") UUID draftVersionId,
                           @Param("updatedAt") LocalDateTime updatedAt);

    int updateStatus(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
                     @Param("status") String status, @Param("pausedBy") String pausedBy,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /** Publish: point at the reviewed version, clear the post-review flag, record self-approval. */
    int updatePublish(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
                      @Param("publishedVersionId") UUID publishedVersionId,
                      @Param("selfApproved") boolean selfApproved, @Param("updatedAt") LocalDateTime updatedAt);

    // --- review queue + audit ----------------------------------------------------------
    List<Quest> findForReview(@Param("statuses") List<String> statuses,
                              @Param("limit") int limit, @Param("offset") int offset);

    long countForReview(@Param("statuses") List<String> statuses);

    void insertReviewDecision(com.ds.goroute.quest.domain.QuestReviewDecisionRow decision);

    void insertReviewComment(com.ds.goroute.quest.domain.QuestReviewComment comment);

    List<com.ds.goroute.quest.domain.QuestReviewDecisionRow> findDecisionsByQuest(@Param("questId") UUID questId);

    List<com.ds.goroute.quest.domain.QuestReviewComment> findCommentsByQuest(@Param("questId") UUID questId);

    // --- discovery (public gate, §6.3) -------------------------------------------------
    List<com.ds.goroute.quest.domain.QuestListItem> findPublicQuests(
            @Param("provinceCode") String provinceCode, @Param("language") String language,
            @Param("limit") int limit, @Param("offset") int offset);

    long countPublicQuests(@Param("provinceCode") String provinceCode, @Param("language") String language);

    com.ds.goroute.quest.domain.QuestListItem findPublicQuestDetail(@Param("id") UUID id);

    // --- versions ----------------------------------------------------------------------
    void insertVersion(QuestVersion version);

    QuestVersion findVersionById(@Param("id") UUID id);

    /** Rewrites the scalar fields of a draft version in place (the graph is replaced separately). */
    int updateVersionContent(QuestVersion version);

    // --- checkpoints / questions / choices / notes (graph) ------------------------------
    List<QuestCheckpoint> findCheckpointsByVersion(@Param("versionId") UUID versionId);

    int countCheckpointsByVersion(@Param("versionId") UUID versionId);

    List<QuestQuestion> findQuestionsByCheckpointIds(@Param("checkpointIds") List<UUID> checkpointIds);

    List<QuestQuestionChoice> findChoicesByQuestionIds(@Param("questionIds") List<UUID> questionIds);

    List<QuestCreatorNote> findNotesByCheckpointIds(@Param("checkpointIds") List<UUID> checkpointIds);

    void insertCheckpoint(QuestCheckpoint checkpoint);

    void insertQuestion(QuestQuestion question);

    void insertChoice(QuestQuestionChoice choice);

    void insertNote(QuestCreatorNote note);

    /** Wipe the whole content graph of a version, deepest table first (no physical FKs). */
    int deleteChoicesByVersion(@Param("versionId") UUID versionId);

    int deleteQuestionsByVersion(@Param("versionId") UUID versionId);

    int deleteNotesByVersion(@Param("versionId") UUID versionId);

    int deleteCheckpointsByVersion(@Param("versionId") UUID versionId);
}

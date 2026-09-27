package com.ds.goroute.quest.persistence;

import com.ds.goroute.quest.domain.QuestEntitlement;
import com.ds.goroute.quest.domain.QuestLocationSample;
import com.ds.goroute.quest.domain.QuestRun;
import com.ds.goroute.quest.domain.QuestRunCheckpoint;
import com.ds.goroute.quest.domain.QuestRunClue;
import com.ds.goroute.quest.domain.QuestRunMember;
import com.ds.goroute.quest.domain.QuestRunQuestion;
import com.ds.goroute.quest.domain.QuestRunStopVisit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Persistence for a quest play-through (§6.2). */
@Mapper
public interface QuestRunMapper {

    // --- run ---------------------------------------------------------------------------
    void insertRun(QuestRun run);

    QuestRun findRunById(@Param("id") UUID id);

    QuestRun findRunByClientId(@Param("ownerUserId") UUID ownerUserId, @Param("clientRunId") String clientRunId);

    QuestRun findOpenRun(@Param("questId") UUID questId, @Param("ownerUserId") UUID ownerUserId);

    int updateRunStatus(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
                        @Param("status") String status, @Param("completedAt") LocalDateTime completedAt,
                        @Param("lastActivityAt") LocalDateTime lastActivityAt);

    int touchRun(@Param("id") UUID id, @Param("lastActivityAt") LocalDateTime lastActivityAt);

    int countCompletedRunsForVersion(@Param("questId") UUID questId, @Param("versionId") UUID versionId);

    int countRunsByStatusSince(@Param("questId") UUID questId, @Param("status") String status,
                               @Param("since") LocalDateTime since);

    // --- members -----------------------------------------------------------------------
    void insertMember(QuestRunMember member);

    QuestRunMember findMemberByRunAndUser(@Param("runId") UUID runId, @Param("userId") UUID userId);

    List<QuestRunMember> findMembersByRun(@Param("runId") UUID runId);

    int markMemberRewarded(@Param("id") UUID id);

    int updateMemberVerification(@Param("id") UUID id, @Param("verification") String verification,
                                 @Param("presenceCheckpoints") int presenceCheckpoints);

    // --- run checkpoints ---------------------------------------------------------------
    void insertRunCheckpoint(QuestRunCheckpoint checkpoint);

    QuestRunCheckpoint findRunCheckpoint(@Param("memberId") UUID memberId,
                                         @Param("checkpointId") UUID checkpointId);

    List<QuestRunCheckpoint> findRunCheckpointsByMember(@Param("memberId") UUID memberId);

    int updateRunCheckpointSample(QuestRunCheckpoint checkpoint);

    int updateRunCheckpointProximity(QuestRunCheckpoint checkpoint);

    int updateRunCheckpointArTap(QuestRunCheckpoint checkpoint);

    int markMemberArSupported(@Param("id") UUID id);

    int updateRunCheckpointCheckin(@Param("id") UUID id, @Param("checkinId") UUID checkinId,
                                   @Param("checkinState") String checkinState);

    // --- run questions -----------------------------------------------------------------
    void insertRunQuestion(QuestRunQuestion question);

    QuestRunQuestion findRunQuestion(@Param("memberId") UUID memberId, @Param("questionId") UUID questionId);

    List<QuestRunQuestion> findRunQuestionsByMember(@Param("memberId") UUID memberId);

    int updateRunQuestion(QuestRunQuestion question);

    // --- entitlements ------------------------------------------------------------------
    void insertEntitlement(QuestEntitlement entitlement);

    QuestEntitlement findEntitlement(@Param("questId") UUID questId, @Param("userId") UUID userId);

    // --- location samples --------------------------------------------------------------
    void insertLocationSample(QuestLocationSample sample);

    // --- finding clues bought and storytelling points heard (§3.14) ---------------------
    int insertRunClue(QuestRunClue clue);

    List<QuestRunClue> findRunCluesByMember(@Param("memberId") UUID memberId);

    int upsertRunStopVisit(QuestRunStopVisit visit);

    List<QuestRunStopVisit> findRunStopVisitsByMember(@Param("memberId") UUID memberId);
}

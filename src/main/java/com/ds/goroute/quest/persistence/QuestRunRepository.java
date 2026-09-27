package com.ds.goroute.quest.persistence;

import com.ds.goroute.quest.domain.QuestEntitlement;
import com.ds.goroute.quest.domain.QuestLocationSample;
import com.ds.goroute.quest.domain.QuestRun;
import com.ds.goroute.quest.domain.QuestRunCheckpoint;
import com.ds.goroute.quest.domain.QuestRunMember;
import com.ds.goroute.quest.domain.QuestRunQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class QuestRunRepository {

    private final QuestRunMapper mapper;

    public void insertRun(QuestRun run) {
        mapper.insertRun(run);
    }

    public Optional<QuestRun> findRunById(UUID id) {
        return Optional.ofNullable(mapper.findRunById(id));
    }

    public Optional<QuestRun> findOpenRun(UUID questId, UUID ownerUserId) {
        return Optional.ofNullable(mapper.findOpenRun(questId, ownerUserId));
    }

    public boolean updateRunStatus(UUID id, long expectedVersion, String status,
                                   LocalDateTime completedAt, LocalDateTime lastActivityAt) {
        return mapper.updateRunStatus(id, expectedVersion, status, completedAt, lastActivityAt) == 1;
    }

    public void touchRun(UUID id, LocalDateTime lastActivityAt) {
        mapper.touchRun(id, lastActivityAt);
    }

    public int countCompletedRunsForVersion(UUID questId, UUID versionId) {
        return mapper.countCompletedRunsForVersion(questId, versionId);
    }

    public int countRunsByStatusSince(UUID questId, String status, LocalDateTime since) {
        return mapper.countRunsByStatusSince(questId, status, since);
    }

    public void insertMember(QuestRunMember member) {
        mapper.insertMember(member);
    }

    public Optional<QuestRunMember> findMember(UUID runId, UUID userId) {
        return Optional.ofNullable(mapper.findMemberByRunAndUser(runId, userId));
    }

    public List<QuestRunMember> findMembers(UUID runId) {
        return mapper.findMembersByRun(runId);
    }

    public void markMemberRewarded(UUID id) {
        mapper.markMemberRewarded(id);
    }

    public void updateMemberVerification(UUID id, String verification, int presenceCheckpoints) {
        mapper.updateMemberVerification(id, verification, presenceCheckpoints);
    }

    public void insertRunCheckpoint(QuestRunCheckpoint checkpoint) {
        mapper.insertRunCheckpoint(checkpoint);
    }

    public Optional<QuestRunCheckpoint> findRunCheckpoint(UUID memberId, UUID checkpointId) {
        return Optional.ofNullable(mapper.findRunCheckpoint(memberId, checkpointId));
    }

    public List<QuestRunCheckpoint> findRunCheckpoints(UUID memberId) {
        return mapper.findRunCheckpointsByMember(memberId);
    }

    public void updateRunCheckpointSample(QuestRunCheckpoint checkpoint) {
        mapper.updateRunCheckpointSample(checkpoint);
    }

    public void updateRunCheckpointCheckin(UUID id, UUID checkinId, String checkinState) {
        mapper.updateRunCheckpointCheckin(id, checkinId, checkinState);
    }

    public void insertRunQuestion(QuestRunQuestion question) {
        mapper.insertRunQuestion(question);
    }

    public Optional<QuestRunQuestion> findRunQuestion(UUID memberId, UUID questionId) {
        return Optional.ofNullable(mapper.findRunQuestion(memberId, questionId));
    }

    public List<QuestRunQuestion> findRunQuestions(UUID memberId) {
        return mapper.findRunQuestionsByMember(memberId);
    }

    public void updateRunQuestion(QuestRunQuestion question) {
        mapper.updateRunQuestion(question);
    }

    public void insertEntitlement(QuestEntitlement entitlement) {
        mapper.insertEntitlement(entitlement);
    }

    public Optional<QuestEntitlement> findEntitlement(UUID questId, UUID userId) {
        return Optional.ofNullable(mapper.findEntitlement(questId, userId));
    }

    public void insertLocationSample(QuestLocationSample sample) {
        mapper.insertLocationSample(sample);
    }
}

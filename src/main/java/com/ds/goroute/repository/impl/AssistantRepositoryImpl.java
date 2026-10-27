package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.AssistantHistoryMessage;
import com.ds.goroute.entity.AssistantProposal;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.mapper.AssistantMapper;
import com.ds.goroute.repository.AssistantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AssistantRepositoryImpl implements AssistantRepository {

    private final AssistantMapper mapper;

    @Override
    public void insertTurn(AssistantTurn turn) {
        mapper.insertTurn(turn);
    }

    @Override
    public Optional<AssistantTurn> findTurn(UUID turnId) {
        return turnId == null ? Optional.empty() : Optional.ofNullable(mapper.findTurn(turnId));
    }

    @Override
    public Optional<AssistantTurn> lockTurn(UUID turnId) {
        return turnId == null ? Optional.empty() : Optional.ofNullable(mapper.findTurnForUpdate(turnId));
    }

    @Override
    public Optional<AssistantTurn> findRunningTurn(UUID conversationId) {
        return Optional.ofNullable(mapper.findRunningTurn(conversationId));
    }

    @Override
    public Optional<AssistantTurn> findNextQueuedTurn(UUID conversationId) {
        return Optional.ofNullable(mapper.findNextQueuedTurn(conversationId));
    }

    @Override
    public boolean hasOpenTurn(UUID conversationId) {
        return mapper.countOpenTurns(conversationId) > 0;
    }

    @Override
    public boolean hasOpenTurnOfUserElsewhere(UUID userId, UUID conversationId) {
        return userId != null && mapper.countOpenTurnsOfUserElsewhere(userId, conversationId) > 0;
    }

    @Override
    public boolean markTurnRunning(UUID turnId, LocalDateTime startedAt) {
        return mapper.markTurnRunning(turnId, startedAt) == 1;
    }

    @Override
    public boolean appendTurnStep(UUID turnId, String stepAsJsonArray) {
        return mapper.appendTurnStep(turnId, stepAsJsonArray) == 1;
    }

    @Override
    public boolean completeTurn(UUID turnId, UUID replyMessageId, Integer inputTokens, Integer outputTokens,
                                LocalDateTime finishedAt) {
        return mapper.completeTurn(turnId, replyMessageId, inputTokens, outputTokens, finishedAt) == 1;
    }

    @Override
    public boolean failTurn(UUID turnId, String error, UUID replyMessageId, LocalDateTime finishedAt) {
        return mapper.failTurn(turnId, error, replyMessageId, finishedAt) == 1;
    }

    @Override
    public List<UUID> findStaleTurnIds(LocalDateTime runningBefore, LocalDateTime queuedBefore, int limit) {
        return mapper.findStaleTurnIds(runningBefore, queuedBefore, limit);
    }

    @Override
    public List<AssistantHistoryMessage> findRecentMessages(UUID conversationId, long beforeSequence, int limit) {
        if (limit <= 0) return List.of();
        List<AssistantHistoryMessage> newestFirst = new ArrayList<>(
                mapper.findRecentMessages(conversationId, beforeSequence, limit));
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    @Override
    public Optional<AssistantHistoryMessage> findMessage(UUID messageId) {
        return messageId == null ? Optional.empty() : Optional.ofNullable(mapper.findMessage(messageId));
    }

    @Override
    public Optional<UUID> findMessageSender(UUID messageId) {
        return messageId == null ? Optional.empty() : Optional.ofNullable(mapper.findMessageSender(messageId));
    }

    @Override
    public Optional<Long> findMessageSequence(UUID messageId) {
        return messageId == null ? Optional.empty() : Optional.ofNullable(mapper.findMessageSequence(messageId));
    }

    @Override
    public void insertProposal(AssistantProposal proposal) {
        mapper.insertProposal(proposal);
    }

    @Override
    public Optional<AssistantProposal> findProposal(UUID proposalId) {
        return proposalId == null ? Optional.empty() : Optional.ofNullable(mapper.findProposal(proposalId));
    }

    @Override
    public Optional<AssistantProposal> lockProposal(UUID proposalId) {
        return proposalId == null ? Optional.empty() : Optional.ofNullable(mapper.findProposalForUpdate(proposalId));
    }

    @Override
    public boolean resolveProposal(UUID proposalId, String status, UUID resolvedBy, LocalDateTime resolvedAt,
                                   UUID resultActivityId, String failureReason) {
        return mapper.resolveProposal(proposalId, status, resolvedBy, resolvedAt, resultActivityId, failureReason) == 1;
    }

    @Override
    public void attachProposalsToMessage(UUID turnId, UUID messageId) {
        mapper.attachProposalsToMessage(turnId, messageId);
    }

    @Override
    public Optional<String> findMemory(UUID conversationId) {
        return Optional.ofNullable(mapper.findMemory(conversationId)).filter(text -> !text.isBlank());
    }

    @Override
    public void saveMemory(UUID conversationId, String content, UUID turnId, LocalDateTime updatedAt) {
        mapper.upsertMemory(conversationId, content, turnId, updatedAt);
    }
}

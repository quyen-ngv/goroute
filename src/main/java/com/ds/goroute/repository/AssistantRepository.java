package com.ds.goroute.repository;

import com.ds.goroute.entity.AssistantHistoryMessage;
import com.ds.goroute.entity.AssistantProposal;
import com.ds.goroute.entity.AssistantTurn;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of Pengoo's turns and proposals (V228). */
public interface AssistantRepository {

    void insertTurn(AssistantTurn turn);

    Optional<AssistantTurn> findTurn(UUID turnId);

    /** Row-locks the turn for the rest of the transaction. */
    Optional<AssistantTurn> lockTurn(UUID turnId);

    Optional<AssistantTurn> findRunningTurn(UUID conversationId);

    Optional<AssistantTurn> findNextQueuedTurn(UUID conversationId);

    boolean hasOpenTurn(UUID conversationId);

    /** The asker has a question queued or running in another conversation. */
    boolean hasOpenTurnOfUserElsewhere(UUID userId, UUID conversationId);

    boolean markTurnRunning(UUID turnId, LocalDateTime startedAt);

    boolean appendTurnStep(UUID turnId, String stepAsJsonArray);

    boolean completeTurn(UUID turnId, UUID replyMessageId, Integer inputTokens, Integer outputTokens,
                         LocalDateTime finishedAt);

    boolean failTurn(UUID turnId, String error, UUID replyMessageId, LocalDateTime finishedAt);

    List<UUID> findStaleTurnIds(LocalDateTime runningBefore, LocalDateTime queuedBefore, int limit);

    /** Recent readable lines before a sequence number, oldest first. */
    List<AssistantHistoryMessage> findRecentMessages(UUID conversationId, long beforeSequence, int limit);

    Optional<AssistantHistoryMessage> findMessage(UUID messageId);

    Optional<UUID> findMessageSender(UUID messageId);

    Optional<Long> findMessageSequence(UUID messageId);

    void insertProposal(AssistantProposal proposal);

    Optional<AssistantProposal> findProposal(UUID proposalId);

    Optional<AssistantProposal> lockProposal(UUID proposalId);

    boolean resolveProposal(UUID proposalId, String status, UUID resolvedBy, LocalDateTime resolvedAt,
                            UUID resultActivityId, String failureReason);

    void attachProposalsToMessage(UUID turnId, UUID messageId);

    /** Pengoo's running notes for a conversation, if it kept any. */
    Optional<String> findMemory(UUID conversationId);

    void saveMemory(UUID conversationId, String content, UUID turnId, LocalDateTime updatedAt);
}

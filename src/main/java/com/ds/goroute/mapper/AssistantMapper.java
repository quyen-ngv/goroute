package com.ds.goroute.mapper;

import com.ds.goroute.entity.AssistantHistoryMessage;
import com.ds.goroute.entity.AssistantProposal;
import com.ds.goroute.entity.AssistantTurn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AssistantMapper {

    int insertTurn(AssistantTurn turn);

    AssistantTurn findTurn(@Param("id") UUID id);

    AssistantTurn findTurnForUpdate(@Param("id") UUID id);

    /** The RUNNING turn of a conversation, if any. */
    AssistantTurn findRunningTurn(@Param("conversationId") UUID conversationId);

    /** The oldest QUEUED turn of a conversation, if any. */
    AssistantTurn findNextQueuedTurn(@Param("conversationId") UUID conversationId);

    long countOpenTurns(@Param("conversationId") UUID conversationId);

    long countOpenTurnsOfUserElsewhere(@Param("userId") UUID userId, @Param("conversationId") UUID conversationId);

    int markTurnRunning(@Param("id") UUID id, @Param("startedAt") LocalDateTime startedAt);

    /** Appends one step (a one-element JSON array) to a RUNNING turn. */
    int appendTurnStep(@Param("id") UUID id, @Param("step") String step);

    int completeTurn(@Param("id") UUID id,
                     @Param("replyMessageId") UUID replyMessageId,
                     @Param("inputTokens") Integer inputTokens,
                     @Param("outputTokens") Integer outputTokens,
                     @Param("finishedAt") LocalDateTime finishedAt);

    /** Fails an open turn and takes it off the allowance. */
    int failTurn(@Param("id") UUID id,
                 @Param("error") String error,
                 @Param("replyMessageId") UUID replyMessageId,
                 @Param("finishedAt") LocalDateTime finishedAt);

    /** Open turns that have run (RUNNING) or waited (QUEUED) longer than allowed. */
    List<UUID> findStaleTurnIds(@Param("runningBefore") LocalDateTime runningBefore,
                                @Param("queuedBefore") LocalDateTime queuedBefore,
                                @Param("limit") int limit);

    /** Recent readable lines before {@code beforeSequence}, newest first. */
    List<AssistantHistoryMessage> findRecentMessages(@Param("conversationId") UUID conversationId,
                                                     @Param("beforeSequence") long beforeSequence,
                                                     @Param("limit") int limit);

    /** One message with its attachments, or null when it does not exist or was deleted. */
    AssistantHistoryMessage findMessage(@Param("messageId") UUID messageId);

    /** Sender of one message, or null when the message does not exist. */
    UUID findMessageSender(@Param("messageId") UUID messageId);

    Long findMessageSequence(@Param("messageId") UUID messageId);

    int insertProposal(AssistantProposal proposal);

    AssistantProposal findProposal(@Param("id") UUID id);

    AssistantProposal findProposalForUpdate(@Param("id") UUID id);

    int resolveProposal(@Param("id") UUID id,
                        @Param("status") String status,
                        @Param("resolvedBy") UUID resolvedBy,
                        @Param("resolvedAt") LocalDateTime resolvedAt,
                        @Param("resultActivityId") UUID resultActivityId,
                        @Param("failureReason") String failureReason);

    int attachProposalsToMessage(@Param("turnId") UUID turnId, @Param("messageId") UUID messageId);

    String findMemory(@Param("conversationId") UUID conversationId);

    int upsertMemory(@Param("conversationId") UUID conversationId,
                     @Param("content") String content,
                     @Param("turnId") UUID turnId,
                     @Param("updatedAt") LocalDateTime updatedAt);
}

package com.ds.goroute.service;

import com.ds.goroute.dto.response.UserBlockResponse;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Who may write to whom.
 *
 * <p>Blocking is the other half of reporting. Reporting is what somebody does after an
 * unwanted message arrives and it ends with a moderator; blocking is what stops the next
 * one and ends with nobody. Direct messages are reachable from any profile, so both are
 * needed and neither substitutes for the other.
 */
public interface UserBlockService {

    /**
     * Records that {@code blockerId} does not want to hear from {@code blockedId}.
     *
     * <p>Idempotent. Blocking somebody already blocked refreshes the reason and nothing else.
     */
    UserBlockResponse block(UUID blockerId, UUID blockedId, String reason);

    void unblock(UUID blockerId, UUID blockedId);

    List<UserBlockResponse> listBlocked(UUID blockerId);

    /**
     * Whether a direct conversation between these two is allowed at all.
     *
     * <p>Blind to direction on purpose: a conversation needs both sides willing, and
     * revealing which of them said no would hand the block back to the person it was made
     * about.
     */
    boolean blockedBetween(UUID first, UUID second);

    /**
     * Of {@code candidates}, those {@code viewer} may not exchange messages with.
     *
     * <p>For lists, so a page of people costs one query rather than one per row.
     */
    Set<UUID> blockedAmong(UUID viewer, Collection<UUID> candidates);
}

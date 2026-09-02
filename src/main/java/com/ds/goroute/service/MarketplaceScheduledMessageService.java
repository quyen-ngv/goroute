package com.ds.goroute.service;

import com.ds.goroute.dto.request.UpsertScheduledMessageRequest;
import com.ds.goroute.dto.response.ScheduledMessageRunResponse;
import com.ds.goroute.dto.response.ScheduledMessageResponse;
import com.ds.goroute.entity.MarketplaceScheduledMessage;
import com.ds.goroute.entity.ScheduledMessageTarget;
import com.ds.goroute.type.MarketplaceScheduledMessageRunStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled guest messages: partner-authored rules and the delivery the job drives.
 *
 * <p>The authoring half needs {@code CHAT_WRITE} on the organization. The three job-facing methods
 * are deliberately on the same interface so the job goes through the Spring proxy and
 * {@link #deliver} gets its own transaction per booking.
 */
public interface MarketplaceScheduledMessageService {
    List<ScheduledMessageResponse> list(UUID actorUserId, UUID organizationId);
    ScheduledMessageResponse create(UUID actorUserId, UUID organizationId, UpsertScheduledMessageRequest request);
    ScheduledMessageResponse update(UUID actorUserId, UUID organizationId, UUID id, UpsertScheduledMessageRequest request);
    void delete(UUID actorUserId, UUID organizationId, UUID id);
    List<ScheduledMessageRunResponse> runs(UUID actorUserId, UUID organizationId, UUID id, int page, int size);

    /** Rules the job must consider on this tick. */
    List<MarketplaceScheduledMessage> findEnabledRules(int limit);

    /**
     * Bookings this rule is due for: trigger moment passed inside the look-back window, booking still
     * deliverable, no run row yet. Never returns a booking twice for the same rule.
     */
    List<ScheduledMessageTarget> findDueTargets(MarketplaceScheduledMessage rule, LocalDateTime now, int limit);

    /**
     * Sends one message in its own transaction. Reserves the run row first, so a concurrent job run
     * racing on the same pair loses on the unique index and rolls its message back before the guest
     * ever sees it.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException when another run already claimed the pair
     */
    MarketplaceScheduledMessageRunStatus deliver(MarketplaceScheduledMessage rule, ScheduledMessageTarget target);

    /** Records a terminal failure in its own transaction, after {@link #deliver} rolled back. */
    void recordFailure(MarketplaceScheduledMessage rule, ScheduledMessageTarget target, String detail);
}

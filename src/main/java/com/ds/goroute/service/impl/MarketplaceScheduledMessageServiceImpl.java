package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.SendMarketplaceMessageRequest;
import com.ds.goroute.dto.request.StartMarketplaceConversationRequest;
import com.ds.goroute.dto.request.UpsertScheduledMessageRequest;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.dto.response.ScheduledMessageResponse;
import com.ds.goroute.dto.response.ScheduledMessageRunResponse;
import com.ds.goroute.entity.MarketplaceScheduledMessage;
import com.ds.goroute.entity.MarketplaceScheduledMessageRun;
import com.ds.goroute.entity.ScheduledMessageTarget;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.MarketplaceScheduledMessageRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.service.MarketplaceScheduledMessageService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.scheduledmessage.ScheduledMessagePlaceholders;
import com.ds.goroute.type.MarketplaceBookingStatus;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.MarketplaceMessageType;
import com.ds.goroute.type.MarketplaceScheduledMessageAudience;
import com.ds.goroute.type.MarketplaceScheduledMessageRunStatus;
import com.ds.goroute.type.MarketplaceScheduledMessageStatus;
import com.ds.goroute.type.MarketplaceScheduledMessageTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Partner-authored automatic messages.
 *
 * <p>Delivery deliberately goes through {@link MarketplaceChatService}, not through the chat mapper:
 * the conversation is created by the very code path the app uses ({@code start}), the message gets
 * its sequence number, idempotency check and WebSocket broadcast from {@code send}, and the guest's
 * client cannot tell an automatic message from a typed one. Duplicating that SQL here would have
 * meant duplicating four invariants that are easy to get subtly wrong.
 *
 * <p>The sender is the organization owner. The owner is the one actor guaranteed to exist for every
 * organization and to hold every permission, so no automation ever depends on an employee who may
 * later be deactivated.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceScheduledMessageServiceImpl implements MarketplaceScheduledMessageService {
    /** Same ceiling as quick replies: this is an automation list a human reads, not a rules engine. */
    static final int MAX_RULES_PER_ORGANIZATION = 50;
    /** Trigger moments older than this are not chased; a rule enabled today must not replay history. */
    static final int LOOKBACK_HOURS = 48;
    private static final String PERMISSION = "CHAT_WRITE";
    private static final int MAX_RUN_PAGE_SIZE = 100;

    /** A conversation in either state means the partner or admin closed the channel on purpose. */
    private static final Set<MarketplaceConversationStatus> MUTED_CONVERSATIONS =
            EnumSet.of(MarketplaceConversationStatus.BLOCKED, MarketplaceConversationStatus.CLOSED);
    /** Mirrors the SQL filter; re-checked inside the delivery transaction against a late cancellation. */
    private static final Set<MarketplaceBookingStatus> DELIVERABLE = EnumSet.of(
            MarketplaceBookingStatus.CONFIRMED, MarketplaceBookingStatus.CHECKED_IN, MarketplaceBookingStatus.COMPLETED);

    private final MarketplaceScheduledMessageRepository repository;
    private final PartnerAuthorizationService authorization;
    private final MarketplaceChatService chatService;

    // ---------------------------------------------------------------- authoring

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledMessageResponse> list(UUID actorUserId, UUID organizationId) {
        authorization.requirePermission(organizationId, actorUserId, PERMISSION);
        return repository.findByOrganization(organizationId, MAX_RULES_PER_ORGANIZATION)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ScheduledMessageResponse create(UUID actorUserId, UUID organizationId, UpsertScheduledMessageRequest request) {
        authorization.requirePermission(organizationId, actorUserId, PERMISSION);
        validate(request);
        if (repository.countByOrganization(organizationId) >= MAX_RULES_PER_ORGANIZATION) {
            throw badRequest("An organization can keep at most " + MAX_RULES_PER_ORGANIZATION + " scheduled messages");
        }
        LocalDateTime now = LocalDateTime.now();
        MarketplaceScheduledMessage rule = MarketplaceScheduledMessage.builder()
                .id(UUID.randomUUID()).organizationId(organizationId).templateId(request.getTemplateId())
                .triggerType(request.getTrigger().name()).offsetHours(request.getOffsetHours())
                .body(request.getBody().trim()).appliesTo(request.getAppliesTo().name())
                .status(status(request).name())
                .createdBy(actorUserId).updatedBy(actorUserId).createdAt(now).updatedAt(now)
                .build();
        repository.insert(rule);
        return toResponse(rule);
    }

    @Override
    @Transactional
    public ScheduledMessageResponse update(UUID actorUserId, UUID organizationId, UUID id,
                                           UpsertScheduledMessageRequest request) {
        authorization.requirePermission(organizationId, actorUserId, PERMISSION);
        validate(request);
        MarketplaceScheduledMessage rule = required(id, organizationId);
        rule.setTemplateId(request.getTemplateId());
        rule.setTriggerType(request.getTrigger().name());
        rule.setOffsetHours(request.getOffsetHours());
        rule.setBody(request.getBody().trim());
        rule.setAppliesTo(request.getAppliesTo().name());
        rule.setStatus(status(request).name());
        rule.setUpdatedBy(actorUserId);
        rule.setUpdatedAt(LocalDateTime.now());
        if (repository.update(rule) != 1) throw notFound();
        return toResponse(required(id, organizationId));
    }

    @Override
    @Transactional
    public void delete(UUID actorUserId, UUID organizationId, UUID id) {
        authorization.requirePermission(organizationId, actorUserId, PERMISSION);
        required(id, organizationId);
        repository.delete(id, organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledMessageRunResponse> runs(UUID actorUserId, UUID organizationId, UUID id, int page, int size) {
        authorization.requirePermission(organizationId, actorUserId, PERMISSION);
        required(id, organizationId);
        int limit = Math.min(Math.max(size, 1), MAX_RUN_PAGE_SIZE);
        int offset = Math.max(page, 0) * limit;
        return repository.findRuns(id, limit, offset).stream()
                .map(run -> ScheduledMessageRunResponse.builder()
                        .id(run.getId()).scheduledMessageId(run.getScheduledMessageId())
                        .bookingType(run.getBookingType()).hotelBookingId(run.getHotelBookingId())
                        .activityOrderId(run.getActivityOrderId()).bookingCode(run.getBookingCode())
                        .conversationId(run.getConversationId()).messageId(run.getMessageId())
                        .status(run.getStatus()).detail(run.getDetail()).sentAt(run.getSentAt())
                        .build())
                .toList();
    }

    // ---------------------------------------------------------------- job

    @Override
    @Transactional(readOnly = true)
    public List<MarketplaceScheduledMessage> findEnabledRules(int limit) {
        return repository.findEnabled(limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledMessageTarget> findDueTargets(MarketplaceScheduledMessage rule, LocalDateTime now, int limit) {
        MarketplaceScheduledMessageTrigger trigger = trigger(rule);
        MarketplaceScheduledMessageAudience audience = audience(rule);
        String zone = ZoneId.systemDefault().getId();
        int offsetHours = rule.getOffsetHours() == null ? 0 : rule.getOffsetHours();
        List<ScheduledMessageTarget> targets = new ArrayList<>();
        // A rule for ALL still only matches the product line its trigger can describe: a stay has no
        // slot start, an activity has no check-out, so BEFORE_ACTIVITY never touches hotel bookings.
        if (audience.includesHotels() && trigger.appliesToHotels()) {
            targets.addAll(repository.findDueHotelTargets(rule.getId(), rule.getOrganizationId(), trigger.name(),
                    offsetHours, now, zone, LOOKBACK_HOURS, limit));
        }
        if (audience.includesActivities() && trigger.appliesToActivities()) {
            targets.addAll(repository.findDueActivityTargets(rule.getId(), rule.getOrganizationId(), trigger.name(),
                    offsetHours, now, zone, LOOKBACK_HOURS, limit));
        }
        return targets;
    }

    @Override
    @Transactional
    public MarketplaceScheduledMessageRunStatus deliver(MarketplaceScheduledMessage rule, ScheduledMessageTarget target) {
        // Reserve first. The unique index on (rule, booking) is the whole concurrency story: a second
        // job run reaching this line for the same pair fails here, before any message is written.
        MarketplaceScheduledMessageRun run = newRun(rule, target, MarketplaceScheduledMessageRunStatus.SENT, null);
        repository.insertRun(run);

        String current = currentStatus(target);
        if (!isDeliverable(current)) {
            return skip(run, "Booking is no longer deliverable (" + current + ")");
        }

        MarketplaceConversationResponse conversation = chatService.start(target.getOwnerUserId(), startRequest(target));
        if (isMuted(conversation.getStatus())) {
            return skip(run, "Conversation is " + conversation.getStatus());
        }

        MarketplaceMessageResponse message = chatService.send(target.getOwnerUserId(), conversation.getId(),
                sendRequest(rule, target));
        repository.updateRunOutcome(run.getId(), MarketplaceScheduledMessageRunStatus.SENT.name(), null,
                conversation.getId(), message.getId());
        return MarketplaceScheduledMessageRunStatus.SENT;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(MarketplaceScheduledMessage rule, ScheduledMessageTarget target, String detail) {
        repository.insertRun(newRun(rule, target, MarketplaceScheduledMessageRunStatus.FAILED, trim(detail)));
    }

    // ---------------------------------------------------------------- helpers

    private MarketplaceScheduledMessageRunStatus skip(MarketplaceScheduledMessageRun run, String detail) {
        repository.updateRunOutcome(run.getId(), MarketplaceScheduledMessageRunStatus.SKIPPED.name(), trim(detail),
                null, null);
        return MarketplaceScheduledMessageRunStatus.SKIPPED;
    }

    private MarketplaceScheduledMessageRun newRun(MarketplaceScheduledMessage rule, ScheduledMessageTarget target,
                                                  MarketplaceScheduledMessageRunStatus status, String detail) {
        return MarketplaceScheduledMessageRun.builder()
                .id(UUID.randomUUID()).scheduledMessageId(rule.getId()).bookingType(target.getBookingType())
                .hotelBookingId(target.getHotelBookingId()).activityOrderId(target.getActivityOrderId())
                .sentAt(LocalDateTime.now()).status(status.name()).detail(detail)
                .build();
    }

    private StartMarketplaceConversationRequest startRequest(ScheduledMessageTarget target) {
        StartMarketplaceConversationRequest request = new StartMarketplaceConversationRequest();
        if (target.isHotel()) {
            request.setConversationType(MarketplaceConversationType.HOTEL_BOOKING);
            request.setHotelBookingId(target.getHotelBookingId());
        } else {
            request.setConversationType(MarketplaceConversationType.ACTIVITY_ORDER);
            request.setActivityOrderId(target.getActivityOrderId());
        }
        return request;
    }

    private SendMarketplaceMessageRequest sendRequest(MarketplaceScheduledMessage rule, ScheduledMessageTarget target) {
        SendMarketplaceMessageRequest request = new SendMarketplaceMessageRequest();
        // Deterministic client id: a second delivery attempt that somehow got past the run row would
        // still be de-duplicated by the chat service's own idempotency check.
        request.setClientMessageId("scheduled:" + rule.getId());
        request.setMessageType(MarketplaceMessageType.TEXT);
        request.setContent(ScheduledMessagePlaceholders.apply(rule.getBody(), target));
        return request;
    }

    private String currentStatus(ScheduledMessageTarget target) {
        return (target.isHotel()
                ? repository.findHotelBookingStatus(target.getHotelBookingId())
                : repository.findActivityOrderStatus(target.getActivityOrderId()))
                .orElse("MISSING");
    }

    private boolean isDeliverable(String status) {
        try {
            return DELIVERABLE.contains(MarketplaceBookingStatus.valueOf(status));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isMuted(String status) {
        try {
            return status != null && MUTED_CONVERSATIONS.contains(MarketplaceConversationStatus.valueOf(status));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void validate(UpsertScheduledMessageRequest request) {
        MarketplaceScheduledMessageTrigger trigger = request.getTrigger();
        MarketplaceScheduledMessageAudience audience = request.getAppliesTo();
        // A rule nothing can ever match is a bug the partner should see now, not a silent no-op later.
        if (audience == MarketplaceScheduledMessageAudience.HOTEL && !trigger.appliesToHotels()) {
            throw badRequest(trigger + " only applies to activity orders");
        }
        if (audience == MarketplaceScheduledMessageAudience.ACTIVITY && !trigger.appliesToActivities()) {
            throw badRequest(trigger + " only applies to hotel bookings");
        }
    }

    private MarketplaceScheduledMessageStatus status(UpsertScheduledMessageRequest request) {
        return request.getStatus() == null ? MarketplaceScheduledMessageStatus.ENABLED : request.getStatus();
    }

    private MarketplaceScheduledMessageTrigger trigger(MarketplaceScheduledMessage rule) {
        return MarketplaceScheduledMessageTrigger.valueOf(rule.getTriggerType());
    }

    private MarketplaceScheduledMessageAudience audience(MarketplaceScheduledMessage rule) {
        return MarketplaceScheduledMessageAudience.valueOf(rule.getAppliesTo());
    }

    private MarketplaceScheduledMessage required(UUID id, UUID organizationId) {
        return repository.findById(id, organizationId).orElseThrow(this::notFound);
    }

    private ScheduledMessageResponse toResponse(MarketplaceScheduledMessage rule) {
        return ScheduledMessageResponse.builder()
                .id(rule.getId()).organizationId(rule.getOrganizationId()).templateId(rule.getTemplateId())
                .trigger(rule.getTriggerType()).offsetHours(rule.getOffsetHours()).body(rule.getBody())
                .appliesTo(rule.getAppliesTo()).status(rule.getStatus())
                .lastRunAt(rule.getLastRunAt()).sentCount(rule.getSentCount() == null ? 0L : rule.getSentCount())
                .createdAt(rule.getCreatedAt()).updatedAt(rule.getUpdatedAt())
                .build();
    }

    private static String trim(String detail) {
        if (detail == null) return null;
        return detail.length() <= 500 ? detail : detail.substring(0, 500);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorConstant.BAD_REQUEST, message);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorConstant.NOT_FOUND, "Scheduled message not found");
    }
}

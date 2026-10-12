package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AssistantReplyRequest;
import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.request.UpdateActivityRequest;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.dto.response.AssistantProposalResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.AssistantProposal;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.WebSocketService;
import com.ds.goroute.type.AssistantProposalAction;
import com.ds.goroute.type.AssistantProposalStatus;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pengoo's proposed changes to a trip. The assistant only ever writes a proposal; applying it is
 * a person's action, performed with that person's own permission (owner or editor) through the
 * normal activity service, so the trip's history, notifications and realtime events name them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantProposalService {

    static final String EVENT_PROPOSAL_UPDATED = "ASSISTANT_PROPOSAL_UPDATED";
    private static final Pattern CATEGORY = Pattern.compile("[a-z_]{2,40}");

    private final AssistantRepository repository;
    private final TripRepository trips;
    private final ActivityRepository activities;
    private final PlaceRepository places;
    private final ActivityService activityService;
    private final TripAccessGuard tripAccessGuard;
    private final MarketplaceConversationAccessService conversationAccess;
    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper;

    /**
     * Stores the valid proposals of one answer and returns their ids in order. Invalid ones are
     * dropped, never repaired: a proposal names a real activity of the trip, a real day, a real place.
     *
     * @param conversationTripId the trip of the thread (a trip chat); null in the 1:1 chat, where a
     *                           proposal names its own trip and the asker must be able to edit it
     */
    @Transactional
    public List<UUID> createFromReply(AssistantTurn turn, UUID conversationTripId,
                                      List<AssistantReplyRequest.ProposalDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) return List.of();
        List<UUID> created = new ArrayList<>();
        for (AssistantReplyRequest.ProposalDraft draft : drafts) {
            try {
                buildProposal(turn, conversationTripId, draft).ifPresent(proposal -> {
                    repository.insertProposal(proposal);
                    created.add(proposal.getId());
                });
            } catch (RuntimeException e) {
                log.info("Assistant turn {}: proposal dropped ({})", turn.getId(), e.getMessage());
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public AssistantProposalResponse get(UUID viewerId, UUID proposalId) {
        AssistantProposal proposal = repository.findProposal(proposalId).orElseThrow(this::notFound);
        conversationAccess.requireAccess(proposal.getConversationId(), viewerId);
        return response(proposal);
    }

    /**
     * Applies a pending proposal as the viewer. Idempotent: a proposal already resolved is returned
     * as it is. A proposal whose activity is gone becomes STALE instead of failing the request.
     */
    @Transactional
    public AssistantProposalResponse apply(UUID viewerId, UUID proposalId) {
        AssistantProposal proposal = repository.lockProposal(proposalId).orElseThrow(this::notFound);
        conversationAccess.requireAccess(proposal.getConversationId(), viewerId);
        if (!AssistantProposalStatus.PENDING.name().equals(proposal.getStatus())) {
            return reload(proposalId);
        }
        tripAccessGuard.requireEditAccess(proposal.getTripId(), viewerId);
        Map<String, Object> payload = payload(proposal);
        AssistantProposalAction action = AssistantProposalAction.valueOf(proposal.getAction());
        LocalDateTime now = LocalDateTime.now();
        UUID activityId = uuid(payload.get("activityId"));
        if (action != AssistantProposalAction.ADD && activityOfTrip(proposal.getTripId(), activityId).isEmpty()) {
            return resolve(proposal, AssistantProposalStatus.STALE, viewerId, now, null, "Activity no longer exists");
        }
        UUID resultActivityId = switch (action) {
            case ADD -> addPlace(proposal, payload, viewerId);
            case MOVE -> {
                activityService.updateActivity(proposal.getTripId(), activityId, UpdateActivityRequest.builder()
                        .dayNumber(integer(payload.get("toDayNumber")))
                        .startTime(time(payload.get("startTime")))
                        .endTime(time(payload.get("endTime")))
                        .build(), viewerId);
                yield activityId;
            }
            case UPDATE_TIME -> {
                activityService.updateActivity(proposal.getTripId(), activityId, UpdateActivityRequest.builder()
                        .startTime(time(payload.get("startTime")))
                        .endTime(time(payload.get("endTime")))
                        .build(), viewerId);
                yield activityId;
            }
            case REMOVE -> {
                activityService.deleteActivity(proposal.getTripId(), activityId, viewerId);
                yield activityId;
            }
        };
        return resolve(proposal, AssistantProposalStatus.APPLIED, viewerId, now, resultActivityId, null);
    }

    @Transactional
    public AssistantProposalResponse dismiss(UUID viewerId, UUID proposalId) {
        AssistantProposal proposal = repository.lockProposal(proposalId).orElseThrow(this::notFound);
        conversationAccess.requireAccess(proposal.getConversationId(), viewerId);
        if (!AssistantProposalStatus.PENDING.name().equals(proposal.getStatus())) {
            return reload(proposalId);
        }
        tripAccessGuard.requireEditAccess(proposal.getTripId(), viewerId);
        return resolve(proposal, AssistantProposalStatus.DISMISSED, viewerId, LocalDateTime.now(), null, null);
    }

    private UUID addPlace(AssistantProposal proposal, Map<String, Object> payload, UUID viewerId) {
        UUID placeId = uuid(payload.get("placeId"));
        Place place = places.findById(placeId).orElseThrow(this::notFound);
        ActivityResponse created = activityService.createActivity(proposal.getTripId(), CreateActivityRequest.builder()
                .name(place.getTitle())
                .placeId(place.getId().toString())
                .address(place.getAddress())
                .lat(place.getLatitude())
                .lng(place.getLongitude())
                .dayNumber(integer(payload.get("dayNumber")))
                .startTime(time(payload.get("startTime")))
                .endTime(time(payload.get("endTime")))
                .category(text(payload.get("category")))
                .build(), viewerId);
        return created.getId();
    }

    private AssistantProposalResponse resolve(AssistantProposal proposal, AssistantProposalStatus status, UUID viewerId,
                                              LocalDateTime at, UUID resultActivityId, String reason) {
        repository.resolveProposal(proposal.getId(), status.name(), viewerId, at, resultActivityId, reason);
        AssistantProposalResponse response = reload(proposal.getId());
        broadcastAfterCommit(proposal.getConversationId(), viewerId, response);
        return response;
    }

    private AssistantProposalResponse reload(UUID proposalId) {
        return response(repository.findProposal(proposalId).orElseThrow(this::notFound));
    }

    private Optional<AssistantProposal> buildProposal(AssistantTurn turn, UUID conversationTripId,
                                                      AssistantReplyRequest.ProposalDraft draft) {
        AssistantProposalAction action = AssistantProposalAction.valueOf(draft.getAction());
        UUID tripId = conversationTripId != null ? conversationTripId : uuid(draft.getTripId());
        if (tripId == null) return Optional.empty();
        Trip trip = trips.findById(tripId).filter(t -> !Boolean.TRUE.equals(t.getIsDeleted())).orElse(null);
        // In a trip chat anyone may ask, and a member who can edit applies; in the 1:1 chat the
        // proposal names one of the asker's own trips, which they must be able to edit.
        boolean ownTripInDirectChat = conversationTripId == null;
        if (trip == null || (ownTripInDirectChat && !tripAccessGuard.canEdit(trip, turn.getUserId()))) {
            return Optional.empty();
        }
        int days = AssistantTripSnapshots.dayCount(trip);
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (action) {
            case ADD -> {
                Place place = places.findById(uuid(draft.getPlaceId()))
                        .filter(p -> p.getVisibilityStatus() == null || p.getVisibilityStatus() == PlaceVisibilityStatus.ACTIVE)
                        .orElse(null);
                if (place == null || !validDay(draft.getDayNumber(), days)) return Optional.empty();
                payload.put("placeId", place.getId().toString());
                payload.put("placeName", place.getTitle());
                payload.put("dayNumber", draft.getDayNumber());
                payload.put("category", category(draft.getCategory(), place));
            }
            case MOVE -> {
                Activity activity = activityOfTrip(tripId, uuid(draft.getActivityId())).orElse(null);
                if (activity == null || !validDay(draft.getToDayNumber(), days)) return Optional.empty();
                putActivity(payload, activity);
                payload.put("toDayNumber", draft.getToDayNumber());
            }
            case UPDATE_TIME -> {
                Activity activity = activityOfTrip(tripId, uuid(draft.getActivityId())).orElse(null);
                if (activity == null || draft.getStartTime() == null) return Optional.empty();
                putActivity(payload, activity);
            }
            case REMOVE -> {
                Activity activity = activityOfTrip(tripId, uuid(draft.getActivityId())).orElse(null);
                if (activity == null) return Optional.empty();
                putActivity(payload, activity);
            }
        }
        if (draft.getStartTime() != null) payload.put("startTime", draft.getStartTime());
        if (draft.getEndTime() != null && draft.getStartTime() != null
                && time(draft.getEndTime()).isAfter(time(draft.getStartTime()))) {
            payload.put("endTime", draft.getEndTime());
        }
        String summary = draft.getSummary() == null ? null : draft.getSummary().trim();
        return Optional.of(AssistantProposal.builder()
                .id(UUID.randomUUID())
                .conversationId(turn.getConversationId())
                .turnId(turn.getId())
                .tripId(tripId)
                .requestedBy(turn.getUserId())
                .action(action.name())
                .payload(json(payload))
                .summary(summary == null || summary.isEmpty() ? null : summary)
                .status(AssistantProposalStatus.PENDING.name())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Optional<Activity> activityOfTrip(UUID tripId, UUID activityId) {
        if (activityId == null) return Optional.empty();
        return activities.findById(activityId).filter(a -> tripId.equals(a.getTripId()));
    }

    private static void putActivity(Map<String, Object> payload, Activity activity) {
        payload.put("activityId", activity.getId().toString());
        payload.put("activityName", activity.getName());
        payload.put("dayNumber", activity.getDayNumber());
    }

    private static boolean validDay(Integer day, int days) {
        return day != null && day >= 1 && day <= days;
    }

    /** The app's activity categories are lower-case words; a food place defaults to a restaurant. */
    private static String category(String requested, Place place) {
        if (requested != null && CATEGORY.matcher(requested.trim().toLowerCase(Locale.ROOT)).matches()) {
            return requested.trim().toLowerCase(Locale.ROOT);
        }
        return place.getPlaceGroup() == PlaceGroup.FOOD_AND_DRINK ? "restaurant" : "attraction";
    }

    AssistantProposalResponse response(AssistantProposal proposal) {
        Map<String, Object> payload = payload(proposal);
        return AssistantProposalResponse.builder()
                .id(proposal.getId())
                .conversationId(proposal.getConversationId())
                .tripId(proposal.getTripId())
                .action(proposal.getAction())
                .status(proposal.getStatus())
                .summary(proposal.getSummary())
                .dayNumber(integer(payload.get("dayNumber")))
                .toDayNumber(integer(payload.get("toDayNumber")))
                .startTime(text(payload.get("startTime")))
                .endTime(text(payload.get("endTime")))
                .placeId(uuid(payload.get("placeId")))
                .placeName(text(payload.get("placeName")))
                .activityId(uuid(payload.get("activityId")))
                .activityName(text(payload.get("activityName")))
                .category(text(payload.get("category")))
                .resolvedByUserId(proposal.getResolvedBy())
                .resolvedByName(proposal.getResolvedByName())
                .resolvedAt(proposal.getResolvedAt())
                .resultActivityId(proposal.getResultActivityId())
                .failureReason(proposal.getFailureReason())
                .build();
    }

    private void broadcastAfterCommit(UUID conversationId, UUID actorId, AssistantProposalResponse response) {
        Map<String, Object> data = objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {
        });
        Runnable send = () -> webSocketService.broadcastToConversation(conversationId, EVENT_PROPOSAL_UPDATED, data, actorId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private Map<String, Object> payload(AssistantProposal proposal) {
        try {
            return proposal.getPayload() == null ? Map.of()
                    : objectMapper.readValue(proposal.getPayload(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static UUID uuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalTime time(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return LocalTime.parse(value.toString().trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorConstant.NOT_FOUND, "Proposal not found");
    }
}

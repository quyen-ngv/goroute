package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.request.AssistantReplyRequest;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.meetup.service.MeetupService;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.TripAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Turns the worker's call-to-action drafts into {@code ACTION} blocks: buttons under an answer
 * that open a screen of the app (create a trip with the destination and dates filled in, open a
 * trip, an event or a meetup). A button never does anything by itself; the screen it opens asks
 * the user as usual.
 *
 * <p>Every draft is checked as if the asker typed the link: the trip must be one they can read,
 * the event published, the meetup visible to them. A draft that fails a check is dropped, never
 * repaired.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantActionService {

    static final int MAX_ACTIONS = 3;
    static final int MAX_DESTINATIONS = 4;
    static final int MAX_TRIP_DAYS = 30;
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TripAccessGuard tripAccessGuard;
    private final LocalEventRepository events;
    private final MeetupService meetups;
    private final LocationImageRepository areas;

    /**
     * @param allowed the chat filter for the label; returns null when the label is blocked
     */
    public List<Map<String, Object>> blocks(AssistantTurn turn, List<AssistantReplyRequest.ActionDraft> drafts,
                                            UnaryOperator<String> allowed) {
        if (drafts == null || drafts.isEmpty()) return List.of();
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (AssistantReplyRequest.ActionDraft draft : drafts) {
            if (blocks.size() >= MAX_ACTIONS) break;
            String label = draft.getLabel() == null ? null : allowed.apply(draft.getLabel());
            if (label == null || draft.getAction() == null) continue;
            try {
                Map<String, Object> block = build(turn, draft);
                if (block == null || blocks.contains(withLabel(block, label))) continue;
                blocks.add(withLabel(block, label));
            } catch (BusinessException | IllegalArgumentException e) {
                log.debug("Assistant action {} dropped: {}", draft.getAction(), e.getMessage());
            }
        }
        return blocks;
    }

    /**
     * Destination cards: catalogue areas that exist, each with its name, slogan and photo for the
     * card, and dates for the create-trip screen when they are usable (same rule as CREATE_TRIP).
     *
     * @param allowed the chat filter for the note; returns null when the note is blocked
     */
    public List<Map<String, Object>> destinationBlocks(List<AssistantReplyRequest.DestinationRef> refs,
                                                       UnaryOperator<String> allowed) {
        if (refs == null || refs.isEmpty()) return List.of();
        List<Map<String, Object>> blocks = new ArrayList<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (AssistantReplyRequest.DestinationRef ref : refs) {
            if (blocks.size() >= MAX_DESTINATIONS) break;
            UUID id = AssistantProposalService.uuid(ref.getId());
            if (id == null || !seen.add(id)) continue;
            LocationImage area = areas.findById(id).orElse(null);
            if (area == null) continue;
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("kind", "DESTINATION");
            block.put("cityId", area.getId().toString());
            block.put("name", area.getFullAddress());
            if (area.getSlogan() != null && !area.getSlogan().isBlank()) block.put("slogan", area.getSlogan().trim());
            String image = area.getImageUrl() != null && !area.getImageUrl().isBlank() ? area.getImageUrl() : area.getAvatarUrl();
            if (image != null) block.put("imageUrl", image);
            String note = ref.getNote() == null ? null : allowed.apply(ref.getNote());
            if (note != null) block.put("note", note);
            putUsableDates(block, ref.getStartDate(), ref.getEndDate());
            blocks.add(block);
        }
        return blocks;
    }

    private Map<String, Object> build(AssistantTurn turn, AssistantReplyRequest.ActionDraft draft) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("kind", "ACTION");
        block.put("action", draft.getAction());
        switch (draft.getAction()) {
            case "CREATE_TRIP" -> {
                if (!createTrip(block, draft)) return null;
            }
            case "OPEN_TRIP" -> {
                UUID tripId = AssistantProposalService.uuid(draft.getTripId());
                if (tripId == null || !tripAccessGuard.canRead(tripId, turn.getUserId())) return null;
                block.put("tripId", tripId.toString());
            }
            case "OPEN_EVENT" -> {
                UUID eventId = AssistantProposalService.uuid(draft.getEventId());
                if (eventId == null || events.findPublishedById(eventId).isEmpty()) return null;
                block.put("eventId", eventId.toString());
            }
            case "OPEN_MEETUP" -> {
                UUID meetupId = AssistantProposalService.uuid(draft.getMeetupId());
                if (meetupId == null) return null;
                // Throws (404 or feature off) when the asker may not see it.
                meetups.detail(turn.getUserId(), meetupId);
                block.put("meetupId", meetupId.toString());
            }
            default -> {
                return null;
            }
        }
        return block;
    }

    /** Destination and dates are hints for the create-trip screen; either may be missing. */
    private boolean createTrip(Map<String, Object> block, AssistantReplyRequest.ActionDraft draft) {
        String city = draft.getCity() == null ? null : draft.getCity().trim();
        if (city != null && !city.isEmpty()) {
            LocationImage area = areas.findBestMatch(AssistantToolService.normalize(city))
                    .flatMap(found -> areas.findById(found.getId()))
                    .orElse(null);
            if (area != null) {
                block.put("cityId", area.getId().toString());
                block.put("destination", area.getFullAddress());
            } else {
                block.put("destination", city);
            }
        }
        putUsableDates(block, draft.getStartDate(), draft.getEndDate());
        return true;
    }

    /** Both dates, from today on, in order and under the trip length cap; otherwise neither. */
    private static void putUsableDates(Map<String, Object> block, String startDate, String endDate) {
        LocalDate start = date(startDate);
        LocalDate end = date(endDate);
        LocalDate today = LocalDate.now(LOCAL_ZONE);
        boolean datesUsable = start != null && end != null && !start.isBefore(today) && !end.isBefore(start)
                && ChronoUnit.DAYS.between(start, end) < MAX_TRIP_DAYS;
        if (datesUsable) {
            block.put("startDate", start.toString());
            block.put("endDate", end.toString());
        }
    }

    private static Map<String, Object> withLabel(Map<String, Object> block, String label) {
        Map<String, Object> labelled = new LinkedHashMap<>(block);
        labelled.put("label", label.length() > 60 ? label.substring(0, 60).trim() : label);
        return labelled;
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

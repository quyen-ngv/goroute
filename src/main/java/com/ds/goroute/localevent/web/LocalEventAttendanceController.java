package com.ds.goroute.localevent.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.dto.LocalEventAddToTripRequest;
import com.ds.goroute.localevent.dto.LocalEventAttendanceResponse;
import com.ds.goroute.localevent.dto.MyLocalEventResponse;
import com.ds.goroute.localevent.service.LocalEventAttendanceService;
import com.ds.goroute.localevent.service.LocalEventTimes;
import com.ds.goroute.localevent.service.LocalEventTripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Going to a local event and its group chat (epic 17). Authenticated (the default rule), unlike
 * the public event reads under /v1/api/public/events; the switch and every rule are enforced in
 * {@link LocalEventAttendanceService}.
 */
@RestController
@RequestMapping("/v1/api/events")
@RequiredArgsConstructor
public class LocalEventAttendanceController {

    private final LocalEventAttendanceService attendanceService;
    private final LocalEventTripService tripService;

    /**
     * The caller's saved and going-to events that end after {@code from} (ISO-8601 with an
     * offset or Z; default now), soonest first. The check-in composer passes the start of the
     * visit day so an event that ended earlier that day is still offered.
     */
    @GetMapping("/mine")
    public ResponseEntity<BaseResponse<List<MyLocalEventResponse>>> mine(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String from) {
        Instant fromInstant = from == null || from.isBlank() ? null
                : LocalEventTimes.parseInstant(from).orElseThrow(() -> new BusinessException(
                        ErrorConstant.INVALID_PARAMETERS, "from must be ISO-8601 with an offset or Z"));
        return ResponseEntity.ok(BaseResponse.ofSucceeded(attendanceService.mine(userId, fromInstant)));
    }

    @PostMapping("/{eventId}/save")
    public ResponseEntity<BaseResponse<LocalEventAttendanceResponse>> save(
            @CurrentUser UUID userId,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(attendanceService.save(userId, eventId)));
    }

    @DeleteMapping("/{eventId}/save")
    public ResponseEntity<BaseResponse<LocalEventAttendanceResponse>> unsave(
            @CurrentUser UUID userId,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(attendanceService.unsave(userId, eventId)));
    }

    /**
     * Adds the event to a trip the caller may edit, as one itinerary stop made from the event
     * (its name, venue, coordinates and cover) on the day and at the time the caller chose.
     */
    @PostMapping("/{eventId}/trips/{tripId}/activities")
    public ResponseEntity<BaseResponse<ActivityResponse>> addToTrip(
            @CurrentUser UUID userId,
            @PathVariable UUID eventId,
            @PathVariable UUID tripId,
            @Valid @RequestBody LocalEventAddToTripRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(tripService.addToTrip(userId, eventId, tripId, request)));
    }

    @GetMapping("/{eventId}/attendance")
    public ResponseEntity<BaseResponse<LocalEventAttendanceResponse>> attendance(
            @CurrentUser UUID userId,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(attendanceService.attendance(userId, eventId)));
    }

    @PostMapping("/{eventId}/join")
    public ResponseEntity<BaseResponse<LocalEventAttendanceResponse>> join(
            @CurrentUser UUID userId,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(attendanceService.join(userId, eventId)));
    }

    @PostMapping("/{eventId}/leave")
    public ResponseEntity<BaseResponse<LocalEventAttendanceResponse>> leave(
            @CurrentUser UUID userId,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(attendanceService.leave(userId, eventId)));
    }

    /** The event's group chat; after opening, everything goes through the ordinary chat endpoints. */
    @GetMapping("/{eventId}/conversation")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> conversation(
            @CurrentUser UUID userId,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(attendanceService.conversation(userId, eventId)));
    }
}

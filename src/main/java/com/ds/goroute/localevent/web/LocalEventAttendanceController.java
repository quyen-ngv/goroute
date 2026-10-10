package com.ds.goroute.localevent.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.localevent.dto.LocalEventAttendanceResponse;
import com.ds.goroute.localevent.service.LocalEventAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

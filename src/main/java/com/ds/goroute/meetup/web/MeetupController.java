package com.ds.goroute.meetup.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.meetup.dto.MeetupCancelRequest;
import com.ds.goroute.meetup.dto.MeetupContextResponse;
import com.ds.goroute.meetup.dto.MeetupDetailResponse;
import com.ds.goroute.meetup.dto.MeetupInviteCandidateResponse;
import com.ds.goroute.meetup.dto.MeetupJoinRequest;
import com.ds.goroute.meetup.dto.MeetupJoinResponse;
import com.ds.goroute.meetup.dto.MeetupParticipantResponse;
import com.ds.goroute.meetup.dto.MeetupSummaryResponse;
import com.ds.goroute.meetup.dto.MeetupUpsertRequest;
import com.ds.goroute.meetup.service.MeetupChatService;
import com.ds.goroute.meetup.service.MeetupService;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupViewerRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The app's meetup API (SPEC_V2 §2.4). Authenticated (the default rule); the feature gate, the
 * visibility rules and every business rule are enforced in {@link MeetupService}.
 */
@Validated
@RestController
@RequestMapping("/v1/api/meetups")
@RequiredArgsConstructor
public class MeetupController {

    private final MeetupService meetupService;
    private final MeetupChatService meetupChatService;

    @GetMapping("/context")
    public ResponseEntity<BaseResponse<MeetupContextResponse>> context(@CurrentUser UUID userId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.context(userId)));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<MeetupSummaryResponse>>> discover(
            @CurrentUser UUID userId,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int radiusKm,
            @RequestParam(required = false) List<MeetupCategory> category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                meetupService.discover(userId, lat, lng, radiusKm, category, page, size)));
    }

    @GetMapping("/mine")
    public ResponseEntity<BaseResponse<PageResponse<MeetupSummaryResponse>>> mine(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "HOST") MeetupViewerRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.mine(userId, role, page, size)));
    }

    @GetMapping("/{meetupId}")
    public ResponseEntity<BaseResponse<MeetupDetailResponse>> detail(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.detail(userId, meetupId)));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<MeetupDetailResponse>> create(
            @CurrentUser UUID userId,
            @Valid @RequestBody MeetupUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(meetupService.create(userId, request)));
    }

    @PutMapping("/{meetupId}")
    public ResponseEntity<BaseResponse<MeetupDetailResponse>> update(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @Valid @RequestBody MeetupUpsertRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.update(userId, meetupId, request)));
    }

    @PostMapping("/{meetupId}/withdraw")
    public ResponseEntity<BaseResponse<MeetupDetailResponse>> withdraw(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.withdraw(userId, meetupId)));
    }

    @PostMapping("/{meetupId}/cancel")
    public ResponseEntity<BaseResponse<MeetupDetailResponse>> cancel(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @Valid @RequestBody(required = false) MeetupCancelRequest request) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.cancel(userId, meetupId, reason)));
    }

    @GetMapping("/{meetupId}/participants")
    public ResponseEntity<BaseResponse<List<MeetupParticipantResponse>>> participants(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.participants(userId, meetupId)));
    }

    /**
     * The meetup group chat, created on first use for meetups that predate it. Only the host
     * and JOINED participants may open it; everything after opening goes through the ordinary
     * chat endpoints.
     */
    @GetMapping("/{meetupId}/conversation")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> conversation(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupChatService.open(userId, meetupId)));
    }

    @PostMapping("/{meetupId}/join")
    public ResponseEntity<BaseResponse<MeetupJoinResponse>> join(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @Valid @RequestBody(required = false) MeetupJoinRequest request) {
        String message = request == null ? null : request.getMessage();
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.join(userId, meetupId, message)));
    }

    @PostMapping("/{meetupId}/leave")
    public ResponseEntity<BaseResponse<MeetupJoinResponse>> leave(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.leave(userId, meetupId)));
    }

    @PostMapping("/{meetupId}/participants/{participantUserId}/accept")
    public ResponseEntity<BaseResponse<MeetupParticipantResponse>> accept(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @PathVariable UUID participantUserId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.accept(userId, meetupId, participantUserId)));
    }

    @PostMapping("/{meetupId}/participants/{participantUserId}/decline")
    public ResponseEntity<BaseResponse<MeetupParticipantResponse>> decline(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @PathVariable UUID participantUserId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.decline(userId, meetupId, participantUserId)));
    }

    @PostMapping("/{meetupId}/participants/{participantUserId}/remove")
    public ResponseEntity<BaseResponse<MeetupParticipantResponse>> remove(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @PathVariable UUID participantUserId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                meetupService.removeParticipant(userId, meetupId, participantUserId)));
    }

    /** The host's invite picker: people they follow or who follow them, filtered by {@code q}. */
    @GetMapping("/{meetupId}/invite-candidates")
    public ResponseEntity<BaseResponse<List<MeetupInviteCandidateResponse>>> inviteCandidates(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @RequestParam(required = false) @Size(max = 100) String q) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.inviteCandidates(userId, meetupId, q)));
    }

    @PostMapping("/{meetupId}/participants/{participantUserId}/invite")
    public ResponseEntity<BaseResponse<MeetupParticipantResponse>> invite(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @PathVariable UUID participantUserId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(meetupService.invite(userId, meetupId, participantUserId)));
    }
}

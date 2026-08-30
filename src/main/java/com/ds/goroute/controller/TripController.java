package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.request.CloneTripRequest;
import com.ds.goroute.dto.request.CreateTripRequest;
import com.ds.goroute.dto.request.InviteMemberRequest;
import com.ds.goroute.dto.request.LinkGuestRequest;
import com.ds.goroute.dto.request.MemberRespondRequest;
import com.ds.goroute.dto.request.UpdateGuestNameRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.TripService;
import com.ds.goroute.dto.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/trips")
@RequiredArgsConstructor
@Slf4j
public class TripController extends BaseController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<BaseResponse<TripResponse>> createTrip(
            @Valid @RequestBody CreateTripRequest request,
            @CurrentUser UUID userId) {
        TripResponse trip = tripService.createTrip(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(trip));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<TripResponse>>> getTrips(
            @RequestParam(required = false) String status,
            @CurrentUser UUID userId) {
        List<TripResponse> trips = tripService.getTrips(userId, status);
        return ResponseEntity.ok(ofSucceeded(trips));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<BaseResponse<TripDetailResponse>> getTripDetail(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        TripDetailResponse trip = tripService.getTripDetail(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(trip));
    }

    @GetMapping("/{tripId}/search-bias")
    public ResponseEntity<BaseResponse<TripSearchBiasResponse>> resolveSearchBias(
            @PathVariable UUID tripId,
            @RequestParam int dayNumber,
            @RequestParam(required = false) UUID overrideDestinationId,
            @CurrentUser UUID userId) {
        TripSearchBiasResponse bias = tripService.resolveSearchBias(
                tripId,
                dayNumber,
                userId,
                overrideDestinationId);
        return ResponseEntity.ok(ofSucceeded(bias));
    }

    @PutMapping("/{tripId}")
    public ResponseEntity<BaseResponse<TripResponse>> updateTrip(
            @PathVariable UUID tripId,
            @Valid @RequestBody UpdateTripRequest request,
            @CurrentUser UUID userId) {
        TripResponse trip = tripService.updateTrip(tripId, request, userId);
        return ResponseEntity.ok(ofSucceeded(trip));
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<BaseResponse<Void>> deleteTrip(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        tripService.deleteTrip(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/invitations/pending")
    public ResponseEntity<BaseResponse<List<TripInvitationResponse>>> getPendingInvitations(
            @CurrentUser UUID userId) {
        List<TripInvitationResponse> invitations = tripService.getPendingInvitations(userId);
        return ResponseEntity.ok(ofSucceeded(invitations));
    }

    @GetMapping("/access-requests/pending")
    public ResponseEntity<BaseResponse<List<TripAccessRequestResponse>>> getPendingAccessRequests(
            @CurrentUser UUID userId) {
        List<TripAccessRequestResponse> requests = tripService.getPendingAccessRequests(userId);
        return ResponseEntity.ok(ofSucceeded(requests));
    }

    @PostMapping("/{tripId}/members")
    public ResponseEntity<BaseResponse<TripMemberResponse>> inviteMember(
            @PathVariable UUID tripId,
            @Valid @RequestBody InviteMemberRequest request,
            @CurrentUser UUID userId) {
        TripMemberResponse member = tripService.inviteMember(tripId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(member));
    }

    @PostMapping("/{tripId}/accept")
    public ResponseEntity<BaseResponse<Void>> acceptInvite(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        tripService.acceptInvite(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{tripId}/members/respond")
    public ResponseEntity<BaseResponse<Void>> respondToInvitation(
            @PathVariable UUID tripId,
            @Valid @RequestBody MemberRespondRequest request,
            @CurrentUser UUID userId) {
        tripService.respondToInvitation(tripId, request.getAction(), userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/{tripId}/members/{memberId}")
    public ResponseEntity<BaseResponse<Void>> removeMember(
            @PathVariable UUID tripId,
            @PathVariable UUID memberId,
            @CurrentUser UUID userId) {
        tripService.removeMember(tripId, memberId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PutMapping("/{tripId}/members/{memberId}/role")
    public ResponseEntity<BaseResponse<Void>> updateMemberRole(
            @PathVariable UUID tripId,
            @PathVariable UUID memberId,
            @RequestParam String role,
            @CurrentUser UUID userId) {
        tripService.updateMemberRole(tripId, memberId, role, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/{tripId}/members")
    public ResponseEntity<BaseResponse<List<TripMemberResponse>>> getTripMembers(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        List<TripMemberResponse> members = tripService.getTripMembers(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(members));
    }

    @PostMapping("/{tripId}/members/{guestMemberId}/link")
    public ResponseEntity<BaseResponse<Void>> linkGuestToUser(
            @PathVariable UUID tripId,
            @PathVariable UUID guestMemberId,
            @Valid @RequestBody LinkGuestRequest request,
            @CurrentUser UUID userId) {
        tripService.linkGuestToUser(tripId, guestMemberId, request, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PutMapping("/{tripId}/members/{guestMemberId}/guest-name")
    public ResponseEntity<BaseResponse<Void>> updateGuestName(
            @PathVariable UUID tripId,
            @PathVariable UUID guestMemberId,
            @Valid @RequestBody UpdateGuestNameRequest request,
            @CurrentUser UUID userId) {
        tripService.updateGuestName(tripId, guestMemberId, request.getGuestName(), userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/join-by-code")
    public ResponseEntity<BaseResponse<TripResponse>> joinTripByCode(
            @RequestParam String code,
            @CurrentUser UUID userId) {
        TripResponse trip = tripService.joinTripByCode(code, userId);
        return ResponseEntity.ok(ofSucceeded(trip));
    }

    @PostMapping("/{tripId}/members/{memberId}/accept")
    public ResponseEntity<BaseResponse<Void>> acceptMember(
            @PathVariable UUID tripId,
            @PathVariable UUID memberId,
            @CurrentUser UUID userId) {
        tripService.acceptMember(tripId, memberId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{tripId}/leave")
    public ResponseEntity<BaseResponse<Void>> leaveTrip(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        tripService.leaveTrip(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{tripId}/clone")
    public ResponseEntity<BaseResponse<TripResponse>> cloneTrip(
            @PathVariable UUID tripId,
            @Valid @RequestBody CloneTripRequest request,
            @CurrentUser UUID userId) {
        TripResponse clonedTrip = tripService.cloneTrip(tripId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(clonedTrip));
    }

    @GetMapping("/recent-location")
    public ResponseEntity<BaseResponse<TripRecentLocationResponse>> getRecentLocation(
            @CurrentUser UUID userId) {
        TripRecentLocationResponse location = tripService.getRecentLocation(userId);
        return ResponseEntity.ok(ofSucceeded(location));
    }
}

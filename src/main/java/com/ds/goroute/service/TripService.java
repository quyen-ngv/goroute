package com.ds.goroute.service;

import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TripService {
    TripResponse createTrip(CreateTripRequest request, UUID userId);

    List<TripResponse> getTrips(UUID userId, String status);

    TripDetailResponse getTripDetail(UUID tripId, UUID userId);

    TripResponse updateTrip(UUID tripId, UpdateTripRequest request, UUID userId);

    void deleteTrip(UUID tripId, UUID userId);

    TripMemberResponse inviteMember(UUID tripId, InviteMemberRequest request, UUID userId);

    void linkGuestToUser(UUID tripId, UUID guestMemberId, LinkGuestRequest request, UUID userId);

    void acceptInvite(UUID tripId, UUID userId);

    void declineInvite(UUID tripId, UUID userId);

    void respondToInvitation(UUID tripId, String action, UUID userId);

    void removeMember(UUID tripId, UUID memberId, UUID userId);

    void updateMemberRole(UUID tripId, UUID memberId, String role, UUID userId);

    void updateGuestName(UUID tripId, UUID guestMemberId, String guestName, UUID userId);

    List<TripMemberResponse> getTripMembers(UUID tripId, UUID userId);

    List<TripInvitationResponse> getPendingInvitations(UUID userId);

    List<TripAccessRequestResponse> getPendingAccessRequests(UUID userId);

    PublicTripResponse getPublicTrip(UUID tripId, UUID viewerId);

    TripVoteResponse voteTripHelpful(UUID tripId, UUID userId);

    TripVoteResponse voteTripUnhelpful(UUID tripId, UUID userId);

    TripResponse joinTripByCode(String code, UUID userId);

    void acceptMember(UUID tripId, UUID memberId, UUID userId);

    void leaveTrip(UUID tripId, UUID userId);

    TripResponse cloneTrip(UUID tripId, CloneTripRequest request, UUID userId);

    List<PublicTripResponse> searchPublicTrips(BigDecimal latitude,
                                               BigDecimal longitude,
                                               BigDecimal radiusKm,
                                               String destination,
                                               String keyword,
                                               boolean allPublic,
                                               int page, int size,
                                               UUID excludeUserId);

    TripRecentLocationResponse getRecentLocation(UUID userId);

    TripSearchBiasResponse resolveSearchBias(UUID tripId, int dayNumber, UUID userId, UUID overrideDestinationId);

    List<TripResponse> getProfileTrips(UUID targetUserId, UUID viewerId);
}

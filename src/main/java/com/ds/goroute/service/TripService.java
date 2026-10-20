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
                                               String randomSeed,
                                               boolean preferImages,
                                               boolean recommended,
                                               int page, int size,
                                               UUID viewerId,
                                               UUID excludeUserId);

    /**
     * Public trips owned by the people {@code viewerId} follows, most recently shared first;
     * with a focus point (both non-null) those whose destination is in its province first,
     * then by 100 km ring outwards, each carrying its {@code focusRank}. Deleted owners,
     * taken-down trips and blocks in either direction are left out.
     */
    List<PublicTripResponse> getFollowingTrips(UUID viewerId, Double focusLat, Double focusLng, int page, int size);

    /**
     * The trips among {@code tripIds} that are still public, not deleted and not taken down, as
     * list cards for {@code viewerId}, in the order of the ids. No block check: callers that need
     * one filter on {@code ownerId}.
     */
    List<PublicTripResponse> getPublicTripCards(UUID viewerId, List<UUID> tripIds);

    /**
     * Public trips whose itinerary has the catalogue place {@code placeId}, as list cards,
     * most recently shared first. Private, deleted and taken-down trips are left out, so
     * this can be shorter than the place's {@code tripUseCount}.
     */
    List<PublicTripResponse> getPublicTripsWithPlace(UUID placeId, int page, int size, UUID viewerId);

    /**
     * Recounts the public-search ranking inputs (activity and memory counts, V216) of every
     * trip whose stored numbers drifted. Returns the number of trips fixed.
     */
    int reconcileContentCounts();

    TripRecentLocationResponse getRecentLocation(UUID userId);

    TripSearchBiasResponse resolveSearchBias(UUID tripId, int dayNumber, UUID userId, UUID overrideDestinationId);

    List<TripResponse> getProfileTrips(UUID targetUserId, UUID viewerId);
}

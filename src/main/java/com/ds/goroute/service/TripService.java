package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateTripRequest;
import com.ds.goroute.dto.request.InviteMemberRequest;
import com.ds.goroute.dto.request.LinkGuestRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.dto.response.TripDetailResponse;
import com.ds.goroute.dto.response.TripInvitationResponse;
import com.ds.goroute.dto.response.TripResponse;
import com.ds.goroute.dto.response.TripMemberResponse;
import com.ds.goroute.dto.response.PublicTripResponse;

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
    
    void removeMember(UUID tripId, UUID memberId, UUID userId);
    
    void updateMemberRole(UUID tripId, UUID memberId, String role, UUID userId);
    
    List<TripMemberResponse> getTripMembers(UUID tripId, UUID userId);
    
    List<TripInvitationResponse> getPendingInvitations(UUID userId);
    
    PublicTripResponse getPublicTrip(UUID tripId);
}

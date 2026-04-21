package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateTripRequest;
import com.ds.goroute.dto.request.InviteMemberRequest;
import com.ds.goroute.dto.request.LinkGuestRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.entity.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.TripStatus;
import com.ds.goroute.type.TripVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final CheckinRepository checkinRepository;
    private final TripNoteRepository tripNoteRepository;
    private final NotificationHelper notificationHelper;

    @Override
    @Transactional
    public TripResponse createTrip(CreateTripRequest request, UUID userId) {
        // Validate dates
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Start date must be before end date");
        }

        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .destination(request.getDestination())
                .destinationPlaceId(request.getDestinationPlaceId())
                .destinationLat(request.getDestinationLat())
                .destinationLng(request.getDestinationLng())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .budget(request.getBudget())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .status(TripStatus.PLANNING)
                .visibility(TripVisibility.PRIVATE)
                .shareCode(generateShareCode())
                .ownerId(userId)
                .isDeleted(false)
                .build();

        tripRepository.insert(trip);

        // Add owner as member
        TripMember owner = TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .userId(userId)
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACCEPTED)
                .joinedAt(LocalDateTime.now())
                .build();
        tripMemberRepository.insert(owner);

        log.info("Trip created: {} by user: {}", trip.getId(), userId);
        return mapToTripResponse(trip, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripResponse> getTrips(UUID userId, String status) {
        List<Trip> trips = tripRepository.findByUserId(userId);

        // Filter by status if provided
        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("all")) {
            final String statusFilter = status;
            trips = trips.stream()
                    .filter(t -> t.getStatus().toString().equalsIgnoreCase(statusFilter))
                    .collect(Collectors.toList());
        }

        return trips.stream()
                .map(t -> mapToTripResponse(t, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TripDetailResponse getTripDetail(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Check if user has access
        var member = tripMemberRepository.findByTripIdAndUserId(tripId, userId);
        if (member.isEmpty() && !trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        List<TripMember> members = tripMemberRepository.findByTripId(tripId);
        List<TripMemberResponse> memberResponses = members.stream()
                .map(this::mapToTripMemberResponse)
                .collect(Collectors.toList());

        User owner = userRepository.findById(trip.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Owner not found"));
        UserResponse ownerResponse = mapToUserResponse(owner);

        TripStatsResponse stats = calculateTripStats(tripId);

        return TripDetailResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .coverImageUrl(trip.getCoverImageUrl())
                .destination(trip.getDestination())
                .lat(trip.getDestinationLat())
                .lng(trip.getDestinationLng())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .status(trip.getStatus().toString())
                .budget(trip.getBudget())
                .currency(trip.getCurrency())
                .visibility(trip.getVisibility().toString())
                .shareCode(trip.getShareCode())
                .startingPointName(trip.getStartingPointName())
                .startingPointAddress(trip.getStartingPointAddress())
                .startingPointLat(trip.getStartingPointLat())
                .startingPointLng(trip.getStartingPointLng())
                .startingPointTime(trip.getStartingPointTime())
                .owner(ownerResponse)
                .members(memberResponses)
                .stats(stats)
                .build();
    }

    @Override
    @Transactional
    public TripResponse updateTrip(UUID tripId, UpdateTripRequest request, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Check if user is owner or member of trip
        if (!trip.getOwnerId().equals(userId) && tripMemberRepository.findByTripIdAndUserId(tripId, userId).isEmpty()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only trip members can update trip");
        }

        if (request.getName() != null) trip.setName(request.getName());
        if (request.getCoverImageUrl() != null) trip.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getDestination() != null) trip.setDestination(request.getDestination());
        if (request.getDestinationLat() != null) trip.setDestinationLat(request.getDestinationLat());
        if (request.getDestinationLng() != null) trip.setDestinationLng(request.getDestinationLng());
        if (request.getStartDate() != null) trip.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) trip.setEndDate(request.getEndDate());
        if (request.getBudget() != null) trip.setBudget(request.getBudget());
        if (request.getCurrency() != null) trip.setCurrency(request.getCurrency());
        if (request.getStatus() != null) trip.setStatus(TripStatus.valueOf(request.getStatus()));
        if (request.getVisibility() != null) trip.setVisibility(TripVisibility.valueOf(request.getVisibility()));
        if (request.getShareExpenses() != null) trip.setShareExpenses(request.getShareExpenses());
        if (request.getShareNotes() != null) trip.setShareNotes(request.getShareNotes());

        // Update starting point
        if (request.getStartingPointName() != null) trip.setStartingPointName(request.getStartingPointName());
        if (request.getStartingPointAddress() != null) trip.setStartingPointAddress(request.getStartingPointAddress());
        if (request.getStartingPointLat() != null) trip.setStartingPointLat(request.getStartingPointLat());
        if (request.getStartingPointLng() != null) trip.setStartingPointLng(request.getStartingPointLng());
        if (request.getStartingPointTime() != null) trip.setStartingPointTime(request.getStartingPointTime());

        tripRepository.updateById(trip);
        log.info("Trip updated: {}", tripId);

        notificationHelper.emitTripUpdated(trip, userId);

        return mapToTripResponse(trip, userId);
    }

    @Override
    @Transactional
    public void deleteTrip(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only owner can delete trip");
        }

        tripRepository.deleteById(tripId);
        log.info("Trip deleted: {}", tripId);

        notificationHelper.emitTripDeleted(trip, userId);
    }

    @Override
    @Transactional
    public TripMemberResponse inviteMember(UUID tripId, InviteMemberRequest request, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only owner can invite members");
        }

        TripMember member;

        // Check if adding guest member
        if (Boolean.TRUE.equals(request.getIsGuest())) {
            // Validate guest name
            if (request.getGuestName() == null || request.getGuestName().trim().isEmpty()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Guest name is required");
            }

            // Create guest member
            member = TripMember.builder()
                    .id(UUID.randomUUID())
                    .tripId(tripId)
                    .userId(null) // Guest không có userId
                    .role(MemberRole.valueOf(request.getRole()))
                    .status(MemberStatus.ACCEPTED) // Guest auto accepted
                    .invitedBy(userId)
                    .isGuest(true)
                    .guestName(request.getGuestName()) // Treat as both username and fullName
                    .joinedAt(LocalDateTime.now())
                    .build();

            log.info("Guest member added to trip: {} - {}", tripId, request.getGuestName());
        } else {
            // Original logic for registered user
            if (request.getIdentifier() == null || request.getIdentifier().trim().isEmpty()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Email or username is required");
            }

            User invitedUser;
            String identifier = request.getIdentifier();

            if (identifier.contains("@")) {
                invitedUser = userRepository.findByEmail(identifier)
                        .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found with email: " + identifier));
            } else {
                invitedUser = userRepository.findByUsername(identifier)
                        .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found with username: " + identifier));
            }

            var existingMember = tripMemberRepository.findByTripIdAndUserId(tripId, invitedUser.getId());
            if (existingMember.isPresent()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "User is already a member");
            }

            member = TripMember.builder()
                    .id(UUID.randomUUID())
                    .tripId(tripId)
                    .userId(invitedUser.getId())
                    .role(MemberRole.valueOf(request.getRole()))
                    .status(MemberStatus.PENDING)
                    .invitedBy(userId)
                    .isGuest(false)
                    .build();

            log.info("Member invited to trip: {} - {}", tripId, invitedUser.getId());
        }

        tripMemberRepository.insert(member);

        notificationHelper.emitMemberAdded(member, trip, userId);

        return mapToTripMemberResponse(member);
    }

    @Override
    @Transactional
    public void acceptInvite(UUID tripId, UUID userId) {
        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Invitation not found"));

        member.setStatus(MemberStatus.ACCEPTED);
        member.setJoinedAt(LocalDateTime.now());
        tripMemberRepository.updateById(member);
        log.info("Member accepted invite: {} - {}", tripId, userId);
    }

    @Override
    @Transactional
    public void removeMember(UUID tripId, UUID memberId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only owner can remove members");
        }

        TripMember member = tripMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Member not found"));

        tripMemberRepository.deleteById(memberId);
        log.info("Member removed from trip: {} - {}", tripId, memberId);

        notificationHelper.emitMemberRemoved(member, trip, userId);
    }

    @Override
    @Transactional
    public void updateMemberRole(UUID tripId, UUID memberId, String role, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only owner can update member role");
        }

        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Member not found"));

        member.setRole(MemberRole.valueOf(role));
        tripMemberRepository.updateById(member);
        log.info("Member role updated: {} - {} - {}", tripId, memberId, role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripMemberResponse> getTripMembers(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Check if user has access
        var member = tripMemberRepository.findByTripIdAndUserId(tripId, userId);
        if (member.isEmpty() && !trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        List<TripMember> members = tripMemberRepository.findByTripId(tripId);
        return members.stream()
                .map(this::mapToTripMemberResponse)
                .collect(Collectors.toList());
    }

    private TripResponse mapToTripResponse(Trip trip, UUID userId) {
        TripStatsResponse stats = calculateTripStats(trip.getId());

        return TripResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .coverImageUrl(trip.getCoverImageUrl())
                .destination(trip.getDestination())
                .lat(trip.getDestinationLat())
                .lng(trip.getDestinationLng())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .status(trip.getStatus() != null ? trip.getStatus().toString() : null)
                .budget(trip.getBudget())
                .currency(trip.getCurrency())
                .visibility(trip.getVisibility() != null ? trip.getVisibility().toString() : null)
                .shareCode(trip.getShareCode())
                .shareExpenses(trip.getShareExpenses())
                .shareNotes(trip.getShareNotes())
                .stats(stats)
                .build();
    }

    private TripMemberResponse mapToTripMemberResponse(TripMember member) {
        // Handle guest member
        if (Boolean.TRUE.equals(member.getIsGuest())) {
            return TripMemberResponse.builder()
                    .id(member.getId())
                    .user(UserResponse.builder()
                            .id(null)
                            .fullName(member.getGuestName())
                            .username(member.getGuestName()) // Use guestName as username
                            .email(null)
                            .avatarUrl(null)
                            .build())
                    .role(member.getRole().toString())
                    .status(member.getStatus().toString())
                    .joinedAt(member.getJoinedAt())
                    .isGuest(true)
                    .build();
        }

        // Handle registered user
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));
        return TripMemberResponse.builder()
                .id(member.getId())
                .user(mapToUserResponse(user))
                .role(member.getRole().toString())
                .status(member.getStatus().toString())
                .joinedAt(member.getJoinedAt())
                .isGuest(false)
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        if (user == null) return null;
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .defaultCurrency(user.getDefaultCurrency())
                .language(user.getLanguage())
                .build();
    }

    private TripStatsResponse calculateTripStats(UUID tripId) {
        int totalItems = activityRepository.findByTripId(tripId).size();
        int checkedInItems = checkinRepository.findByTripId(tripId).size();
        int totalMembers = (int) tripMemberRepository.findByTripId(tripId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACCEPTED)
                .count();
        BigDecimal totalExpenses = expenseRepository.findByTripId(tripId).stream()
                .map(e -> e.getAmountInTripCurrency() != null ? e.getAmountInTripCurrency() : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        BigDecimal remainingBudget = trip.getBudget() != null ? trip.getBudget().subtract(totalExpenses) : BigDecimal.ZERO;

        return TripStatsResponse.builder()
                .totalItems(totalItems)
                .checkedInItems(checkedInItems)
                .totalMembers(totalMembers)
                .totalExpenses(totalExpenses)
                .remainingBudget(remainingBudget)
                .build();
    }

    private String generateShareCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public List<TripInvitationResponse> getPendingInvitations(UUID userId) {
        List<TripMember> pendingMembers = tripMemberRepository.findPendingByUserId(userId);

        return pendingMembers.stream().map(member -> {
            Trip trip = tripRepository.findById(member.getTripId())
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

            User inviter = userRepository.findById(member.getInvitedBy())
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Inviter not found"));

            return TripInvitationResponse.builder()
                    .tripId(trip.getId())
                    .tripName(trip.getName())
                    .coverImageUrl(trip.getCoverImageUrl())
                    .destination(trip.getDestination())
                    .startDate(trip.getStartDate())
                    .endDate(trip.getEndDate())
                    .invitedBy(mapToUserResponse(inviter))
                    .role(member.getRole().toString())
                    .invitedAt(member.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void linkGuestToUser(UUID tripId, UUID guestMemberId, LinkGuestRequest request, UUID currentUserId) {
        // Validate guest member
        TripMember guestMember = tripMemberRepository.findById(guestMemberId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Guest member not found"));

        if (!Boolean.TRUE.equals(guestMember.getIsGuest())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Member is not a guest");
        }

        if (!guestMember.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Guest member does not belong to this trip");
        }

        // Validate target user exists in trip
        UUID targetUserId = request.getTargetUserId();
        TripMember targetMember = tripMemberRepository.findByTripIdAndUserId(tripId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Target user is not a member of this trip"));

        if (Boolean.TRUE.equals(targetMember.getIsGuest())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Cannot link to another guest member");
        }

        // Validate current user is owner or editor
        tripMemberRepository.findByTripIdAndUserId(tripId, currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.FORBIDDEN, "You are not a member of this trip"));

        // Update all expense splits for this guest member
        List<ExpenseSplit> guestSplits = expenseSplitRepository.findByGuestMemberId(guestMemberId);
        log.info("Found {} expense splits for guest member {}", guestSplits.size(), guestMemberId);

        for (ExpenseSplit split : guestSplits) {
            split.setUserId(targetUserId);
            split.setGuestMemberId(null);
            split.setGuestName(null);
            expenseSplitRepository.update(split);
        }

        tripMemberRepository.deleteById(guestMemberId);

        log.info("Guest member linked: trip={}, guest={} -> user={}, updated {} expense splits",
                tripId, guestMemberId, targetUserId, guestSplits.size());

        notificationHelper.emitGuestLinked(guestMember, targetUserId, tripId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicTripResponse getPublicTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Get activities
        List<com.ds.goroute.entity.Activity> activities = activityRepository.findByTripId(tripId);
        
        // Get expenses if shared
        Map<UUID, List<PublicExpenseResponse>> expensesByActivity = new java.util.HashMap<>();
        List<PublicExpenseResponse> tripLevelExpenses = new java.util.ArrayList<>();
        
        if (Boolean.TRUE.equals(trip.getShareExpenses())) {
            List<com.ds.goroute.entity.Expense> expenses = expenseRepository.findByTripId(tripId);
            for (com.ds.goroute.entity.Expense e : expenses) {
                PublicExpenseResponse expenseResponse = PublicExpenseResponse.builder()
                        .id(e.getId())
                        .amount(e.getAmount())
                        .currency(e.getCurrency())
                        .category(String.valueOf(e.getCategory()))
                        .description(e.getDescription())
                        .createdAt(e.getCreatedAt())
                        .build();
                
                if (e.getActivityId() != null) {
                    expensesByActivity.computeIfAbsent(e.getActivityId(), k -> new java.util.ArrayList<>()).add(expenseResponse);
                } else {
                    tripLevelExpenses.add(expenseResponse);
                }
            }
        }

        // Get notes if shared
        Map<UUID, List<PublicNoteResponse>> notesByActivity = new java.util.HashMap<>();
        List<PublicNoteResponse> tripLevelNotes = new java.util.ArrayList<>();
        
        if (Boolean.TRUE.equals(trip.getShareNotes())) {
            List<TripNote> notes = tripNoteRepository.findByTripId(tripId);
            for (TripNote n : notes) {
                PublicNoteResponse noteResponse = PublicNoteResponse.builder()
                        .id(n.getId())
                        .content(n.getContent())
                        .createdAt(n.getCreatedAt())
                        .build();
                
                if (n.getActivityId() != null) {
                    notesByActivity.computeIfAbsent(n.getActivityId(), k -> new java.util.ArrayList<>()).add(noteResponse);
                } else {
                    tripLevelNotes.add(noteResponse);
                }
            }
        }
        
        // Build activity responses with nested expenses and notes
        List<PublicActivityResponse> activityResponses = activities.stream()
                .map(a -> PublicActivityResponse.builder()
                        .id(a.getId())
                        .dayNumber(a.getDayNumber())
                        .name(a.getName())
                        .address(a.getAddress())
                        .lat(a.getLat())
                        .lng(a.getLng())
                        .startTime(a.getStartTime())
                        .endTime(a.getEndTime())
                        .category(a.getCategory())
                        .rating(a.getRating())
                        .photoUrl(a.getPhotoUrl())
                        .expenses(expensesByActivity.getOrDefault(a.getId(), null))
                        .notes(notesByActivity.getOrDefault(a.getId(), null))
                        .build())
                .collect(Collectors.toList());

        return PublicTripResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .coverImageUrl(trip.getCoverImageUrl())
                .destination(trip.getDestination())
                .lat(trip.getDestinationLat())
                .lng(trip.getDestinationLng())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .currency(trip.getCurrency())
                .activities(activityResponses)
                .expenses(tripLevelExpenses.isEmpty() ? null : tripLevelExpenses)
                .notes(tripLevelNotes.isEmpty() ? null : tripLevelNotes)
                .build();
    }
}

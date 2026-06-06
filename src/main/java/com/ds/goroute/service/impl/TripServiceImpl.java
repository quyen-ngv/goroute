package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CloneTripRequest;
import com.ds.goroute.dto.request.CreateTripRequest;
import com.ds.goroute.dto.request.InviteMemberRequest;
import com.ds.goroute.dto.request.LinkGuestRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.entity.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.service.LocationImageService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.*;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
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
    private final LocationImageService locationImageService;
    private final ExpenseService expenseService;
    private final UserReviewRepository userReviewRepository;
    private final UserReviewProfileRepository userReviewProfileRepository;
    private final PlaceScoreRepository placeScoreRepository;

    @Override
    @Transactional
    public TripResponse createTrip(CreateTripRequest request, UUID userId) {
        // Validate dates
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Start date must be before end date");
        }

        // Auto set cover image based on destination
        String coverImage = locationImageService.getImageForDestination(request.getDestination());

        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .coverImageUrl(coverImage)
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
                .shareExpenses(false)
                .shareNotes(true)
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

        // Check if user has access (must be ACCEPTED member, not LEFT)
        var member = tripMemberRepository.findByTripIdAndUserId(tripId, userId);
        if (member.isEmpty() || member.get().getStatus() == MemberStatus.LEFT) {
            if (!trip.getOwnerId().equals(userId)) {
                throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
            }
        }

        List<TripMember> members = tripMemberRepository.findByTripId(tripId);
        List<TripMemberResponse> memberResponses = members.stream()
                .map(this::mapToTripMemberResponse)
                .collect(Collectors.toList());

        User owner = userRepository.findById(trip.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Owner not found"));
        UserResponse ownerResponse = mapToUserResponse(owner);

        TripStatsResponse stats = calculateTripStats(tripId);

        // Auto fill cover image if not set
        String coverImageUrl = trip.getCoverImageUrl();
        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
            coverImageUrl = locationImageService.getImageForDestination(trip.getDestination());
        }

        return TripDetailResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .coverImageUrl(coverImageUrl)
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
                .shareExpenses(trip.getShareExpenses())
                .shareNotes(trip.getShareNotes())
                .description(trip.getDescription())
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

        // Check if user is owner or ACCEPTED member of trip (not LEFT)
        var member = tripMemberRepository.findByTripIdAndUserId(tripId, userId);
        boolean hasAccess = trip.getOwnerId().equals(userId) ||
                           (member.isPresent() && member.get().getStatus() == MemberStatus.ACCEPTED);

        if (!hasAccess) {
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
        boolean tripCurrencyChanged = false;
        if (request.getCurrency() != null) {
            String currentCurrency = trip.getCurrency() != null ? trip.getCurrency() : "VND";
            tripCurrencyChanged = !request.getCurrency().equalsIgnoreCase(currentCurrency);
            trip.setCurrency(request.getCurrency());
        }
        if (request.getStatus() != null) trip.setStatus(TripStatus.valueOf(request.getStatus()));
        if (request.getVisibility() != null) trip.setVisibility(TripVisibility.valueOf(request.getVisibility()));
        if (request.getShareExpenses() != null) trip.setShareExpenses(request.getShareExpenses());
        if (request.getShareNotes() != null) trip.setShareNotes(request.getShareNotes());
        if (request.getDescription() != null) trip.setDescription(request.getDescription());

        // Update starting point
        if (request.getStartingPointName() != null) trip.setStartingPointName(request.getStartingPointName());
        if (request.getStartingPointAddress() != null) trip.setStartingPointAddress(request.getStartingPointAddress());
        if (request.getStartingPointLat() != null) trip.setStartingPointLat(request.getStartingPointLat());
        if (request.getStartingPointLng() != null) trip.setStartingPointLng(request.getStartingPointLng());
        if (request.getStartingPointTime() != null) trip.setStartingPointTime(request.getStartingPointTime());

        tripRepository.updateById(trip);
        log.info("Trip updated: {}", tripId);

        if (tripCurrencyChanged) {
            expenseService.recalculateForTripCurrency(tripId);
        }

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
                    .userId(null) // Guest khÃ´ng cÃ³ userId
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
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Invitation not found"));

        if (member.getStatus() != MemberStatus.PENDING) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invitation is not pending");
        }

        member.setStatus(MemberStatus.ACCEPTED);
        member.setJoinedAt(LocalDateTime.now());
        tripMemberRepository.updateById(member);
        log.info("Member accepted invite: {} - {}", tripId, userId);

        notificationHelper.emitMemberAccepted(member, trip, userId);
    }

    @Override
    @Transactional
    public void declineInvite(UUID tripId, UUID userId) {
        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Invitation not found"));

        if (member.getStatus() != MemberStatus.PENDING) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invitation is not pending");
        }

        member.setStatus(MemberStatus.DECLINED);
        tripMemberRepository.updateById(member);
        log.info("Member declined invite: {} - {}", tripId, userId);
    }

    @Override
    @Transactional
    public void respondToInvitation(UUID tripId, String action, UUID userId) {
        if (action == null || action.isBlank()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Action is required");
        }

        switch (action.trim().toLowerCase()) {
            case "accept" -> acceptInvite(tripId, userId);
            case "decline" -> declineInvite(tripId, userId);
            default -> throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid action. Use accept or decline");
        }
    }

    @Override
    @Transactional
    public void removeMember(UUID tripId, UUID memberId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        TripMember member = tripMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Member not found"));

        if (!member.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Member does not belong to this trip");
        }

        boolean isOwner = trip.getOwnerId().equals(userId);
        boolean isSelfDecline = member.getUserId() != null
                && member.getUserId().equals(userId)
                && member.getStatus() == MemberStatus.PENDING;

        if (!isOwner && !isSelfDecline) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only owner can remove members");
        }

        if (member.getUserId() != null && member.getUserId().equals(trip.getOwnerId())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Cannot remove trip owner");
        }

        if (isSelfDecline) {
            member.setStatus(MemberStatus.DECLINED);
            tripMemberRepository.updateById(member);
            log.info("Member declined invite via remove: {} - {}", tripId, userId);
            return;
        }

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
    @Transactional
    public void updateGuestName(UUID tripId, UUID guestMemberId, String guestName, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only owner can update guest name");
        }

        TripMember guestMember = tripMemberRepository.findById(guestMemberId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Guest member not found"));

        if (!guestMember.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Guest member does not belong to this trip");
        }

        if (!Boolean.TRUE.equals(guestMember.getIsGuest())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Member is not a guest");
        }

        guestMember.setGuestName(guestName);
        tripMemberRepository.updateById(guestMember);
        log.info("Guest name updated: {} - {} - {}", tripId, guestMemberId, guestName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripMemberResponse> getTripMembers(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Check if user has access (must be ACCEPTED member, not LEFT)
        var member = tripMemberRepository.findByTripIdAndUserId(tripId, userId);
        boolean hasAccess = trip.getOwnerId().equals(userId) ||
                           (member.isPresent() && member.get().getStatus() == MemberStatus.ACCEPTED);

        if (!hasAccess) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        List<TripMember> members = tripMemberRepository.findByTripId(tripId);
        return members.stream()
                .filter(m -> m.getStatus() != MemberStatus.LEFT && m.getStatus() != MemberStatus.DECLINED)
                .map(this::mapToTripMemberResponse)
                .collect(Collectors.toList());
    }

    private TripResponse mapToTripResponse(Trip trip, UUID userId) {
        TripStatsResponse stats = calculateTripStats(trip.getId());

        // Auto fill cover image if not set
        String coverImageUrl = trip.getCoverImageUrl();
        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
            coverImageUrl = locationImageService.getImageForDestination(trip.getDestination());
        }

        // Determine user role in this trip
        String userRole = null;
        log.info("ðŸ” Mapping trip: {}, ownerId: {}, userId: {}", trip.getName(), trip.getOwnerId(), userId);

        // Check if user is owner first (most important)
        if (trip.getOwnerId() != null && trip.getOwnerId().equals(userId)) {
            userRole = "OWNER";
            log.info("âœ… User is OWNER (from trip.ownerId)");
        } else {
            // Check if user is member
            var member = tripMemberRepository.findByTripIdAndUserId(trip.getId(), userId);
            if (member.isPresent()) {
                // If member role is OWNER, use it (fallback for old data)
                if (member.get().getRole() == MemberRole.OWNER) {
                    userRole = "OWNER";
                    log.info("âœ… User is OWNER (from trip_members)");
                } else {
                    userRole = member.get().getRole().toString();
                    log.info("âœ… User is member with role: {}", userRole);
                }
            } else {
                log.warn("âš ï¸ User is not owner and not member - tripId: {}, userId: {}", trip.getId(), userId);
            }
        }

        return TripResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .coverImageUrl(coverImageUrl)
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
                .description(trip.getDescription())
                .stats(stats)
                .userRole(userRole)
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

    private UUID parseActivityPlaceId(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(placeId);
        } catch (IllegalArgumentException e) {
            log.warn("Activity placeId is not a backend UUID: {}", placeId);
            return null;
        }
    }

    private List<UserReviewResponse> buildSharedTripReviews(
            UUID placeId,
            UUID ownerId,
            Set<UUID> memberUserIds
    ) {
        List<UserReview> reviews = userReviewRepository.findByPlaceId(placeId, 1000, 0).stream()
                .filter(review -> memberUserIds.contains(review.getUserId()))
                .sorted(sharedTripReviewComparator(ownerId))
                .collect(Collectors.toList());

        return reviews.stream()
                .map(this::mapToSharedTripUserReviewResponse)
                .collect(Collectors.toList());
    }

    private Comparator<UserReview> sharedTripReviewComparator(UUID ownerId) {
        return Comparator
                .comparingInt((UserReview review) -> review.getUserId().equals(ownerId) ? 0 : 1)
                .thenComparing(UserReview::getOverallRating, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserReview::getUnhelpfulVotes, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UserReview::getHelpfulVotes, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserReview::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private UserReviewResponse mapToSharedTripUserReviewResponse(UserReview review) {
        User user = userRepository.findById(review.getUserId()).orElse(null);
        UserReviewProfile profile = userReviewProfileRepository.findByUserId(review.getUserId()).orElse(null);

        return UserReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .placeId(review.getPlaceId())
                .tripId(review.getTripId())
                .userName(user != null ? user.getFullName() : "Unknown")
                .userAvatar(user != null ? user.getAvatarUrl() : null)
                .userTier(profile != null ? profile.getTier() : UserTier.NEWCOMER)
                .overallRating(review.getOverallRating())
                .foodRating(review.getFoodRating())
                .priceRating(review.getPriceRating())
                .ambianceRating(review.getAmbianceRating())
                .serviceRating(review.getServiceRating())
                .text(review.getText())
                .photos(parseReviewPhotos(review.getPhotos()))
                .weight(review.getWeight() != null ? review.getWeight() : BigDecimal.ONE)
                .helpfulVotes(review.getHelpfulVotes() != null ? review.getHelpfulVotes() : 0)
                .unhelpfulVotes(review.getUnhelpfulVotes() != null ? review.getUnhelpfulVotes() : 0)
                .hasVotedHelpful(null)
                .isOwnReview(false)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private List<String> parseReviewPhotos(String photosJson) {
        if (photosJson == null || photosJson.isBlank()) {
            return null;
        }
        List<?> parsed = JsonUtils.fromJson(photosJson, List.class);
        if (parsed == null) {
            return null;
        }
        return parsed.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private BigDecimal calculateTripAverage(List<UserReviewResponse> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return null;
        }
        BigDecimal total = reviews.stream()
                .map(UserReviewResponse::getOverallRating)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = reviews.stream()
                .map(UserReviewResponse::getOverallRating)
                .filter(Objects::nonNull)
                .count();
        if (count == 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private PlaceScoreResponse buildPlatformScore(UUID placeId) {
        return placeScoreRepository.findByPlaceId(placeId)
                .map(score -> {
                    BigDecimal displayScore = score.getTripmindScore() != null
                            ? score.getTripmindScore()
                            : score.getGoogleScore();
                    return PlaceScoreResponse.builder()
                            .placeId(placeId)
                            .tripmindScore(score.getTripmindScore())
                            .googleScore(score.getGoogleScore())
                            .reviewCount(score.getReviewCount())
                            .displayScore(displayScore != null ? displayScore.toPlainString() : null)
                            .displayLabel("Platform score")
                            .useGoogleScore(score.getTripmindScore() == null && score.getGoogleScore() != null)
                            .lastCalculatedAt(score.getLastCalculatedAt())
                            .build();
                })
                .orElse(null);
    }

    private String generateShareCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public List<TripInvitationResponse> getPendingInvitations(UUID userId) {
        List<TripMember> pendingMembers = tripMemberRepository.findPendingByUserId(userId);

        return pendingMembers.stream()
                .map(member -> {
                    try {
                        Trip trip = tripRepository.findById(member.getTripId())
                                .orElse(null);

                        if (trip == null) {
                            log.warn("Trip not found for pending invitation: tripId={}", member.getTripId());
                            return null;
                        }

                        User inviter = null;
                        if (member.getInvitedBy() != null) {
                            inviter = userRepository.findById(member.getInvitedBy()).orElse(null);
                        }

                        if (inviter == null) {
                            log.warn("Inviter not found for pending invitation: invitedBy={}, using placeholder", member.getInvitedBy());
                            // Create placeholder inviter
                            inviter = User.builder()
                                    .id(member.getInvitedBy())
                                    .fullName("Unknown User")
                                    .username("unknown")
                                    .build();
                        }

                        // Auto fill cover image if not set
                        String coverImageUrl = trip.getCoverImageUrl();
                        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
                            coverImageUrl = locationImageService.getImageForDestination(trip.getDestination());
                        }

                        return TripInvitationResponse.builder()
                                .memberId(member.getId())
                                .tripId(trip.getId())
                                .tripName(trip.getName())
                                .coverImageUrl(coverImageUrl)
                                .destination(trip.getDestination())
                                .startDate(trip.getStartDate())
                                .endDate(trip.getEndDate())
                                .invitedBy(mapToUserResponse(inviter))
                                .role(member.getRole().toString())
                                .invitedAt(member.getCreatedAt())
                                .build();
                    } catch (Exception e) {
                        log.error("Error processing pending invitation for member: {}, error: {}", member.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull) // Filter out failed invitations
                .collect(Collectors.toList());
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

        if (trip.getVisibility() == TripVisibility.PRIVATE) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Trip is not shared");
        }

        // Increment view count async (don't block response)
        incrementViewCountAsync(tripId);

        // Auto fill cover image if not set
        String coverImageUrl = trip.getCoverImageUrl();
        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
            coverImageUrl = locationImageService.getImageForDestination(trip.getDestination());
        }

        // Get activities
        List<com.ds.goroute.entity.Activity> activities = activityRepository.findByTripId(tripId);
        List<TripMember> acceptedMembers = tripMemberRepository.findByTripId(tripId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACCEPTED)
                .filter(m -> m.getUserId() != null)
                .collect(Collectors.toList());
        Set<UUID> memberUserIds = acceptedMembers.stream()
                .map(TripMember::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        memberUserIds.add(trip.getOwnerId());

        // Get expenses if shared
        Map<UUID, List<PublicExpenseResponse>> expensesByActivity = new java.util.HashMap<>();
        List<PublicExpenseResponse> tripLevelExpenses = new java.util.ArrayList<>();

        if (Boolean.TRUE.equals(trip.getShareExpenses())) {
            List<com.ds.goroute.entity.Expense> expenses = expenseRepository.findByTripId(tripId);
            for (com.ds.goroute.entity.Expense e : expenses) {
                int splitCount = expenseSplitRepository.findByExpenseId(e.getId()).size();
                PublicExpenseResponse expenseResponse = PublicExpenseResponse.builder()
                        .id(e.getId())
                        .amount(e.getAmount())
                        .currency(e.getCurrency())
                        .category(String.valueOf(e.getCategory()))
                        .description(e.getDescription())
                        .splitCount(splitCount)
                        .photoUrls(e.getPhotoUrls() != null ? List.of(e.getPhotoUrls()) : List.of())
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
                if (Boolean.FALSE.equals(n.getIsShared())) {
                    continue;
                }
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
                .map(a -> {
                    UUID placeId = parseActivityPlaceId(a.getPlaceId());
                    List<UserReviewResponse> memberReviews = placeId != null
                            ? buildSharedTripReviews(placeId, trip.getOwnerId(), memberUserIds)
                            : null;
                    return PublicActivityResponse.builder()
                            .id(a.getId())
                            .dayNumber(a.getDayNumber())
                            .placeId(placeId)
                            .bookingId(a.getBookingId())
                            .name(a.getName())
                            .address(a.getAddress())
                            .lat(a.getLat())
                            .lng(a.getLng())
                            .startTime(a.getStartTime())
                            .endTime(a.getEndTime())
                            .category(a.getCategory())
                            .rating(a.getRating())
                            .reviewCount(memberReviews != null ? memberReviews.size() : null)
                            .photoUrl(a.getPhotoUrl())
                            .description(a.getDescription())
                            .platformScore(placeId != null ? buildPlatformScore(placeId) : null)
                            .tripAvgScore(calculateTripAverage(memberReviews))
                            .memberReviews(memberReviews == null || memberReviews.isEmpty() ? null : memberReviews)
                            .expenses(expensesByActivity.getOrDefault(a.getId(), null))
                            .notes(notesByActivity.getOrDefault(a.getId(), null))
                            .build();
                })
                .collect(Collectors.toList());

        // Get owner info
        User owner = userRepository.findById(trip.getOwnerId()).orElse(null);
        String ownerName = owner != null ? owner.getFullName() : null;
        String ownerAvatarUrl = owner != null ? owner.getAvatarUrl() : null;

        return PublicTripResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .coverImageUrl(coverImageUrl)
                .description(trip.getDescription())
                .destination(trip.getDestination())
                .lat(trip.getDestinationLat())
                .lng(trip.getDestinationLng())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .currency(trip.getCurrency())
                .ownerName(ownerName)
                .ownerAvatarUrl(ownerAvatarUrl)
                .activities(activityResponses)
                .expenses(tripLevelExpenses.isEmpty() ? null : tripLevelExpenses)
                .notes(tripLevelNotes.isEmpty() ? null : tripLevelNotes)
                .viewCount(trip.getViewCount())
                .copyCount(trip.getCopyCount())
                .build();
    }

    @Override
    @Transactional
    public TripResponse joinTripByCode(String code, UUID userId) {
        Trip trip = tripRepository.findByShareCode(code)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found with code: " + code));

        // Check if user is already a member
        var existingMember = tripMemberRepository.findByTripIdAndUserId(trip.getId(), userId);
        if (existingMember.isPresent()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "User is already a member of this trip");
        }

        // Create new member with PENDING status
        TripMember member = TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .userId(userId)
                .role(MemberRole.VIEWER)
                .status(MemberStatus.PENDING)
                .build();

        tripMemberRepository.insert(member);
        log.info("User joined trip by code: tripId={}, userId={}, code={}", trip.getId(), userId, code);

        notificationHelper.emitMemberAdded(member, trip, userId);

        return mapToTripResponse(trip, userId);
    }

    @Override
    @Transactional
    public void acceptMember(UUID tripId, UUID memberId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Check if current user is owner
        if (!trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only owner can accept members");
        }

        TripMember member = tripMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Member not found"));

        // Check if member belongs to this trip
        if (!member.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Member does not belong to this trip");
        }

        // Check if member is in PENDING status
        if (member.getStatus() != MemberStatus.PENDING) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Member is not in pending status");
        }

        member.setStatus(MemberStatus.ACCEPTED);
        member.setJoinedAt(LocalDateTime.now());
        tripMemberRepository.updateById(member);
        log.info("Member accepted: tripId={}, memberId={}, acceptedBy={}", tripId, memberId, userId);

        notificationHelper.emitMemberAccepted(member, trip, userId);
    }

    @Override
    @Transactional
    public void leaveTrip(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Owner cannot leave trip
        if (trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Owner cannot leave trip. Please delete the trip or transfer ownership first.");
        }

        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "You are not a member of this trip"));

        // Update status to LEFT instead of deleting
        member.setStatus(MemberStatus.LEFT);
        tripMemberRepository.updateById(member);
        log.info("Member left trip: tripId={}, userId={}", tripId, userId);

        notificationHelper.emitMemberLeft(member, trip, userId);
    }

    @Override
    @Transactional
    public TripResponse cloneTrip(UUID tripId, CloneTripRequest request, UUID userId) {
        // 1. Validate dates
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Start date must be before end date");
        }

        // 2. Get original trip
        Trip originalTrip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // 3. Verify user can clone this trip
        // Allow cloning if:
        // - User is owner
        // - User is accepted member
        // - Trip is public/shared (anyone can clone)
        boolean canClone = originalTrip.getOwnerId().equals(userId) ||
                tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                        .filter(m -> m.getStatus() == MemberStatus.ACCEPTED)
                        .isPresent() ||
                originalTrip.getVisibility() == TripVisibility.PUBLIC ||
                originalTrip.getVisibility() == TripVisibility.SHARED;

        if (!canClone) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You don't have access to clone this trip");
        }

        // 4. Create new trip with user-provided info
        Trip newTrip = Trip.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .coverImageUrl(originalTrip.getCoverImageUrl())
                .destination(originalTrip.getDestination())
                .destinationPlaceId(originalTrip.getDestinationPlaceId())
                .destinationLat(originalTrip.getDestinationLat())
                .destinationLng(originalTrip.getDestinationLng())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .budget(request.getBudget())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .status(TripStatus.PLANNING)
                .visibility(TripVisibility.PRIVATE)
                .shareCode(generateShareCode())
                .timezone(originalTrip.getTimezone())
                .ownerId(userId) // Current user becomes owner
                .description(originalTrip.getDescription())
                .startingPointName(originalTrip.getStartingPointName())
                .startingPointAddress(originalTrip.getStartingPointAddress())
                .startingPointLat(originalTrip.getStartingPointLat())
                .startingPointLng(originalTrip.getStartingPointLng())
                .startingPointTime(originalTrip.getStartingPointTime())
                .shareExpenses(false)
                .shareNotes(true)
                .isDeleted(false)
                .build();

        tripRepository.insert(newTrip);
        log.info("Cloned trip created: originalTripId={}, newTripId={}, userId={}", tripId, newTrip.getId(), userId);

        // 5. Add user as owner member
        TripMember owner = TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(newTrip.getId())
                .userId(userId)
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACCEPTED)
                .joinedAt(LocalDateTime.now())
                .build();
        tripMemberRepository.insert(owner);

        // 6. Increment copy count of original trip
        try {
            tripRepository.incrementCopyCount(tripId);
        } catch (Exception e) {
            log.warn("Failed to increment copy count for trip: {}", tripId, e);
        }

        // 7. Clone activities (without expenses)
        List<Activity> originalActivities = activityRepository.findByTripId(tripId);
        for (Activity originalActivity : originalActivities) {
            Activity newActivity = Activity.builder()
                    .id(UUID.randomUUID())
                    .tripId(newTrip.getId())
                    .dayNumber(originalActivity.getDayNumber())
                    .placeId(originalActivity.getPlaceId())
                    .customPlaceId(originalActivity.getCustomPlaceId())
                    .name(originalActivity.getName())
                    .address(originalActivity.getAddress())
                    .lat(originalActivity.getLat())
                    .lng(originalActivity.getLng())
                    .endLat(originalActivity.getEndLat())
                    .endLng(originalActivity.getEndLng())
                    .endAddress(originalActivity.getEndAddress())
                    .startTime(originalActivity.getStartTime())
                    .endTime(originalActivity.getEndTime())
                    .estimatedCost(originalActivity.getEstimatedCost())
                    .costCurrency(originalActivity.getCostCurrency())
                    .category(originalActivity.getCategory())
                    .transportMode(originalActivity.getTransportMode())
                    .rating(originalActivity.getRating())
                    .photoUrl(originalActivity.getPhotoUrl())
                    .notes(originalActivity.getNotes())
                    .description(originalActivity.getDescription())
                    .status(ActivityStatus.CONFIRMED)
                    .addedBy(userId)
                    .isAccommodation(originalActivity.getIsAccommodation())
                    .isStartingPoint(originalActivity.getIsStartingPoint())
                    .startingPointDate(originalActivity.getStartingPointDate())
                    .build();

            activityRepository.insert(newActivity);
        }

        log.info("Cloned {} activities for trip: {}", originalActivities.size(), newTrip.getId());

        // 8. Return response
        return mapToTripResponse(newTrip, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicTripResponse> searchPublicTrips(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm, String destination, int page, int size, UUID excludeUserId) {
        List<Trip> trips = tripRepository.searchPublicTrips(latitude, longitude, radiusKm, destination, page, size, excludeUserId);

        return trips.stream()
                .map(trip -> {
                    String coverImageUrl = trip.getCoverImageUrl();
                    if (coverImageUrl == null || coverImageUrl.isEmpty()) {
                        coverImageUrl = locationImageService.getImageForDestination(trip.getDestination());
                    }

                    // Get owner info
                    User owner = userRepository.findById(trip.getOwnerId()).orElse(null);
                    String ownerName = owner != null ? owner.getFullName() : null;
                    String ownerAvatarUrl = owner != null ? owner.getAvatarUrl() : null;

                    return PublicTripResponse.builder()
                            .id(trip.getId())
                            .name(trip.getName())
                            .coverImageUrl(coverImageUrl)
                            .description(trip.getDescription())
                            .destination(trip.getDestination())
                            .lat(trip.getDestinationLat())
                            .lng(trip.getDestinationLng())
                            .startDate(trip.getStartDate())
                            .endDate(trip.getEndDate())
                            .currency(trip.getCurrency())
                            .ownerName(ownerName)
                            .ownerAvatarUrl(ownerAvatarUrl)
                            .activities(null)
                            .expenses(null)
                            .notes(null)
                            .viewCount(trip.getViewCount())
                            .copyCount(trip.getCopyCount())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TripRecentLocationResponse getRecentLocation(UUID userId) {
        Trip recentTrip = tripRepository.findMostRecentByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "No trips found for user"));

        return TripRecentLocationResponse.builder()
                .name(recentTrip.getDestination())
                .lat(recentTrip.getDestinationLat())
                .lng(recentTrip.getDestinationLng())
                .build();
    }

    private void incrementViewCountAsync(UUID tripId) {
        new Thread(() -> {
            try {
                tripRepository.incrementViewCount(tripId);
            } catch (Exception e) {
                log.warn("Failed to increment view count for trip: {}", tripId, e);
            }
        }).start();
    }
}

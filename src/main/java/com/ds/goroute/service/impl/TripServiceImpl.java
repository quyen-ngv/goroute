package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.ExpenseSplitCount;
import com.ds.goroute.dto.request.CloneTripRequest;
import com.ds.goroute.dto.request.CreateTripRequest;
import com.ds.goroute.dto.request.InviteMemberRequest;
import com.ds.goroute.dto.request.LinkGuestRequest;
import com.ds.goroute.dto.request.TripDestinationRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.entity.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.LocationImageService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.*;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.MediaAssetResponseMapper;
import com.ds.goroute.utils.MemoryImageUrlNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripServiceImpl implements TripService {
    private final StarService starService;

    private final TripRepository tripRepository;
    private final TripAccessGuard tripAccessGuard;
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
    private final ExchangeRateService exchangeRateService;
    private final UserReviewRepository userReviewRepository;
    private final UserReviewProfileRepository userReviewProfileRepository;
    private final ReviewHelpfulVoteRepository reviewHelpfulVoteRepository;
    private final TripHelpfulVoteRepository tripHelpfulVoteRepository;
    private final PlaceScoreRepository placeScoreRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final TripRealtimePublisher tripRealtimePublisher;
    private final TripDestinationRepository tripDestinationRepository;
    private final SocialNotificationService socialNotificationService;
    private final Executor applicationTaskExecutor;

    private List<TripDestination> buildDestinationsForTrip(
            UUID tripId,
            List<TripDestinationRequest> requests,
            String fallbackDestination,
            String fallbackPlaceId,
            BigDecimal fallbackLat,
            BigDecimal fallbackLng,
            LocalDate tripStartDate,
            LocalDate tripEndDate) {
        List<TripDestination> destinations = new ArrayList<>();
        if (requests != null && !requests.isEmpty()) {
            int index = 0;
            for (TripDestinationRequest request : requests) {
                String name = firstNonBlank(request.getName(), request.getAddress(), fallbackDestination);
                if (name == null) {
                    continue;
                }
                LocalDate startDate = request.getStartDate();
                LocalDate endDate = request.getEndDate();
                if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Destination start date must be before end date");
                }
                if ((startDate != null && (startDate.isBefore(tripStartDate) || startDate.isAfter(tripEndDate))) ||
                        (endDate != null && (endDate.isBefore(tripStartDate) || endDate.isAfter(tripEndDate)))) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Destination date range must be inside trip date range");
                }
                destinations.add(TripDestination.builder()
                        .id(UUID.randomUUID())
                        .tripId(tripId)
                        .name(name)
                        .address(firstNonBlank(request.getAddress(), request.getName()))
                        .placeId(request.getPlaceId())
                        .lat(request.getLat())
                        .lng(request.getLng())
                        .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : index)
                        .startDate(startDate)
                        .endDate(endDate)
                        .isPrimary(destinations.isEmpty() || Boolean.TRUE.equals(request.getIsPrimary()))
                        .build());
                index++;
            }
        }

        if (destinations.isEmpty() && firstNonBlank(fallbackDestination) != null) {
            destinations.add(TripDestination.builder()
                    .id(UUID.randomUUID())
                    .tripId(tripId)
                    .name(fallbackDestination)
                    .address(fallbackDestination)
                    .placeId(fallbackPlaceId)
                    .lat(fallbackLat)
                    .lng(fallbackLng)
                    .orderIndex(0)
                    .startDate(tripStartDate)
                    .endDate(tripEndDate)
                    .isPrimary(true)
                    .build());
        }

        destinations.sort(Comparator.comparing(
                TripDestination::getOrderIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));
        if (!destinations.isEmpty()) {
            for (int i = 0; i < destinations.size(); i++) {
                destinations.get(i).setOrderIndex(i);
                destinations.get(i).setIsPrimary(i == 0);
            }
        }
        return destinations;
    }

    private List<TripDestination> cloneDestinationsForTrip(
            Trip originalTrip,
            UUID newTripId,
            LocalDate newStartDate,
            LocalDate newEndDate) {
        List<TripDestination> originals = tripDestinationRepository.findByTripId(originalTrip.getId());
        if (originals.isEmpty()) {
            originals = buildDestinationsForTrip(
                    originalTrip.getId(),
                    null,
                    originalTrip.getDestination(),
                    originalTrip.getDestinationPlaceId(),
                    originalTrip.getDestinationLat(),
                    originalTrip.getDestinationLng(),
                    originalTrip.getStartDate(),
                    originalTrip.getEndDate()
            );
        }

        List<TripDestination> cloned = new ArrayList<>();
        for (TripDestination original : originals) {
            cloned.add(TripDestination.builder()
                    .id(UUID.randomUUID())
                    .tripId(newTripId)
                    .name(original.getName())
                    .address(original.getAddress())
                    .placeId(original.getPlaceId())
                    .lat(original.getLat())
                    .lng(original.getLng())
                    .orderIndex(original.getOrderIndex())
                    .startDate(newStartDate)
                    .endDate(newEndDate)
                    .isPrimary(Boolean.TRUE.equals(original.getIsPrimary()))
                    .build());
        }
        return cloned;
    }

    private List<TripDestinationResponse> destinationResponses(Trip trip) {
        List<TripDestination> destinations = tripDestinationRepository.findByTripId(trip.getId());
        if (destinations.isEmpty()) {
            destinations = buildDestinationsForTrip(
                    trip.getId(),
                    null,
                    trip.getDestination(),
                    trip.getDestinationPlaceId(),
                    trip.getDestinationLat(),
                    trip.getDestinationLng(),
                    trip.getStartDate(),
                    trip.getEndDate()
            );
        }
        return destinations.stream()
                .sorted(Comparator.comparing(TripDestination::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                .map(this::mapToTripDestinationResponse)
                .toList();
    }

    private TripDestinationResponse mapToTripDestinationResponse(TripDestination destination) {
        return TripDestinationResponse.builder()
                .id(destination.getId())
                .name(destination.getName())
                .address(destination.getAddress())
                .placeId(destination.getPlaceId())
                .lat(destination.getLat())
                .lng(destination.getLng())
                .orderIndex(destination.getOrderIndex())
                .startDate(destination.getStartDate())
                .endDate(destination.getEndDate())
                .isPrimary(destination.getIsPrimary())
                .build();
    }

    private String routeSummary(List<TripDestinationResponse> destinations) {
        if (destinations == null || destinations.isEmpty()) {
            return null;
        }
        return destinations.stream()
                .map(TripDestinationResponse::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(" → "));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Override
    @Transactional
    public TripResponse createTrip(CreateTripRequest request, UUID userId) {
        // Validate dates
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Start date must be before end date");
        }
        starService.reserveTripCreation(userId);

        List<TripDestination> destinations = buildDestinationsForTrip(
                null,
                request.getDestinations(),
                request.getDestination(),
                request.getDestinationPlaceId(),
                request.getDestinationLat(),
                request.getDestinationLng(),
                request.getStartDate(),
                request.getEndDate()
        );
        TripDestination primaryDestination = destinations.isEmpty() ? null : destinations.get(0);
        String destinationName = primaryDestination != null ? primaryDestination.getName() : request.getDestination();
        String destinationAddress = primaryDestination != null ? primaryDestination.getAddress() : request.getDestination();
        String destinationPlaceId = primaryDestination != null ? primaryDestination.getPlaceId() : request.getDestinationPlaceId();
        BigDecimal destinationLat = primaryDestination != null ? primaryDestination.getLat() : request.getDestinationLat();
        BigDecimal destinationLng = primaryDestination != null ? primaryDestination.getLng() : request.getDestinationLng();

        // Auto set cover image based on primary destination
        String coverImage = locationImageService.getImageForDestination(destinationAddress);

        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .coverImageUrl(coverImage)
                .destination(destinationAddress != null ? destinationAddress : destinationName)
                .destinationPlaceId(destinationPlaceId)
                .destinationLat(destinationLat)
                .destinationLng(destinationLng)
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
        destinations.forEach(destination -> destination.setTripId(trip.getId()));
        destinations.forEach(tripDestinationRepository::insert);

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

        // Trip detail is available only to the owner or an ACCEPTED member.
        var member = tripMemberRepository.findByTripIdAndUserId(tripId, userId);
        boolean isOwner = trip.getOwnerId().equals(userId);
        boolean isAcceptedMember = member
                .map(TripMember::getStatus)
                .filter(MemberStatus.ACCEPTED::equals)
                .isPresent();
        if (!isOwner && !isAcceptedMember) {
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

        // Auto fill cover image if not set
        String coverImageUrl = trip.getCoverImageUrl();
        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
            coverImageUrl = locationImageService.getImageForDestination(trip.getDestination());
        }

        List<TripDestinationResponse> destinationResponses = destinationResponses(trip);

        return TripDetailResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .coverImageUrl(coverImageUrl)
                .destination(trip.getDestination())
                .lat(trip.getDestinationLat())
                .lng(trip.getDestinationLng())
                .destinations(destinationResponses)
                .routeSummary(routeSummary(destinationResponses))
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

    @CacheEvict(cacheNames = "publicTripSearch", cacheManager = "searchCacheManager", allEntries = true)
    @Override
    @Transactional
    public TripResponse updateTrip(UUID tripId, UpdateTripRequest request, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // The owner or an accepted EDITOR. A VIEWER sees the trip and cannot rename or re-date it.
        tripAccessGuard.requireEditAccess(tripId, userId);

        if (request.getName() != null) trip.setName(request.getName());
        if (request.getCoverImageUrl() != null) trip.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getDestination() != null) trip.setDestination(request.getDestination());
        if (request.getDestinationLat() != null) trip.setDestinationLat(request.getDestinationLat());
        if (request.getDestinationLng() != null) trip.setDestinationLng(request.getDestinationLng());
        if (request.getDestinations() != null) {
            List<TripDestination> destinations = buildDestinationsForTrip(
                    tripId,
                    request.getDestinations(),
                    trip.getDestination(),
                    trip.getDestinationPlaceId(),
                    trip.getDestinationLat(),
                    trip.getDestinationLng(),
                    trip.getStartDate(),
                    trip.getEndDate()
            );
            tripDestinationRepository.replaceForTrip(tripId, destinations);
            if (!destinations.isEmpty()) {
                TripDestination primary = destinations.get(0);
                trip.setDestination(primary.getAddress() != null ? primary.getAddress() : primary.getName());
                trip.setDestinationPlaceId(primary.getPlaceId());
                trip.setDestinationLat(primary.getLat());
                trip.setDestinationLng(primary.getLng());
            }
        }
        if (request.getStartDate() != null) trip.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) trip.setEndDate(request.getEndDate());
        if (request.getBudget() != null) trip.setBudget(request.getBudget());
        boolean tripCurrencyChanged = false;
        if (request.getCurrency() != null) {
            String currentCurrency = trip.getCurrency() != null ? trip.getCurrency() : "VND";
            tripCurrencyChanged = !request.getCurrency().equalsIgnoreCase(currentCurrency);
            if (tripCurrencyChanged && request.getBudget() == null && trip.getBudget() != null) {
                trip.setBudget(exchangeRateService.convert(
                        trip.getBudget(), currentCurrency, request.getCurrency()));
            }
            trip.setCurrency(request.getCurrency());
        }
        if (request.getStatus() != null) trip.setStatus(TripStatus.valueOf(request.getStatus()));
        TripVisibility previousVisibility = trip.getVisibility();
        if (request.getVisibility() != null) {
            TripVisibility nextVisibility = TripVisibility.valueOf(request.getVisibility());
            trip.setVisibility(nextVisibility);
            if (nextVisibility == TripVisibility.PUBLIC && previousVisibility != TripVisibility.PUBLIC) {
                trip.setPublicSharedAt(LocalDateTime.now());
            }
        }
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
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.TRIP_UPDATED, tripId, tripId, userId);

        return mapToTripResponse(trip, userId);
    }

    @CacheEvict(cacheNames = "publicTripSearch", cacheManager = "searchCacheManager", allEntries = true)
    @Override
    @Transactional
    public void deleteTrip(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only owner can delete trip");
        }

        imageStorageCleanupService.deleteImagesForEntityRecord("TRIP", tripId);
        
        // Publish realtime event before actual deletion so subscribers still exist
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.TRIP_DELETED, tripId, tripId, userId);
        
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
        boolean memberAlreadyPersisted = false;

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

            MemberRole requestedRole = MemberRole.valueOf(request.getRole());
            var existingMember = tripMemberRepository.findByTripIdAndUserId(tripId, invitedUser.getId());
            if (existingMember.isPresent()) {
                member = existingMember.get();
                if (!isReactivatableMember(member)) {
                    throw new BusinessException(ErrorConstant.TRIP_MEMBER_ALREADY_EXISTS);
                }

                // The unique (trip_id, user_id) constraint means a declined/left row
                // must be reactivated instead of inserting a second membership row.
                member.setRole(requestedRole);
                member.setStatus(MemberStatus.PENDING);
                member.setInvitedBy(userId);
                member.setJoinedAt(null);
                member.setCreatedAt(LocalDateTime.now());
                member.setGuestName(null);
                member.setIsGuest(false);
                tripMemberRepository.updateById(member);
                memberAlreadyPersisted = true;
            } else {
                member = TripMember.builder()
                        .id(UUID.randomUUID())
                        .tripId(tripId)
                        .userId(invitedUser.getId())
                        .role(requestedRole)
                        .status(MemberStatus.PENDING)
                        .invitedBy(userId)
                        .isGuest(false)
                        .build();
            }

            log.info("Member invited to trip: {} - {}", tripId, invitedUser.getId());
        }

        if (!memberAlreadyPersisted) {
            // New members and guests are inserted here.
            tripMemberRepository.insert(member);
        }
        tripRealtimePublisher.publishAfterCommit(
                member.getStatus() == MemberStatus.ACCEPTED
                        ? TripRealtimeEventType.MEMBER_ACCEPTED
                        : TripRealtimeEventType.MEMBER_INVITED,
                tripId,
                member.getId(),
                userId);

        if (member.getUserId() != null && member.getStatus() == MemberStatus.PENDING) {
            Map<String, Object> inviteData = new HashMap<>();
            inviteData.put("actorName", notificationHelper.actorName(userId));
            inviteData.put("newMemberName", notificationHelper.actorName(member.getUserId()));
            inviteData.put("tripName", trip.getName());
            inviteData.put("deepLink", "/notifications?tab=invites");
            notificationHelper.emitGeneric(tripId, userId, NotificationType.TRIP_INVITE,
                    inviteData, List.of(member.getUserId()), null);
            notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.MEMBER_INVITED,
                    inviteData, List.of(member.getUserId()));
        } else {
            notificationHelper.emitMemberAdded(member, trip, userId);
        }

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
            throw new BusinessException(ErrorConstant.TRIP_INVITATION_NOT_PENDING);
        }
        if (member.getInvitedBy() == null) {
            throw new BusinessException(
                    ErrorConstant.FORBIDDEN_ERROR,
                    "Join requests must be accepted by the trip owner"
            );
        }

        member.setStatus(MemberStatus.ACCEPTED);
        member.setJoinedAt(LocalDateTime.now());
        tripMemberRepository.updateById(member);
        log.info("Member accepted invite: {} - {}", tripId, userId);
        starService.grant(trip.getOwnerId(), 1, "INVITE_ACCEPTED",
                "invite:" + trip.getOwnerId() + ":" + userId,
                "Your invited traveler joined a trip");

        notificationHelper.emitMemberAccepted(member, trip, userId);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.MEMBER_ACCEPTED, tripId, member.getId(), userId);
    }

    @Override
    @Transactional
    public void declineInvite(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Invitation not found"));

        if (member.getStatus() != MemberStatus.PENDING) {
            throw new BusinessException(ErrorConstant.TRIP_INVITATION_NOT_PENDING);
        }

        member.setStatus(MemberStatus.DECLINED);
        tripMemberRepository.updateById(member);
        log.info("Member declined invite: {} - {}", tripId, userId);
        if (member.getInvitedBy() != null) {
            notificationHelper.emitGeneric(tripId, userId, NotificationType.TRIP_INVITE_DECLINED,
                    Map.of(
                            "memberName", notificationHelper.actorName(userId),
                            "tripName", trip.getName(),
                            "deepLink", "/trip/" + tripId + "/members"
                    ), List.of(member.getInvitedBy()), null);
        }
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.MEMBER_DECLINED, tripId, member.getId(), userId);
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
            if (member.getInvitedBy() != null) {
                notificationHelper.emitGeneric(tripId, userId, NotificationType.TRIP_INVITE_DECLINED,
                        Map.of("memberName", notificationHelper.actorName(userId), "tripName", trip.getName(),
                                "deepLink", "/trip/" + tripId + "/members"),
                        List.of(member.getInvitedBy()), null);
            }
            tripRealtimePublisher.publishAfterCommit(
                    TripRealtimeEventType.MEMBER_DECLINED, tripId, memberId, userId);
            return;
        }

        if (member.getStatus() == MemberStatus.PENDING) {
            tripMemberRepository.deleteById(memberId);
            NotificationType type = member.getInvitedBy() == null
                    ? NotificationType.MEMBER_JOIN_REJECTED
                    : NotificationType.TRIP_INVITE_CANCELLED;
            if (member.getUserId() != null) {
                notificationHelper.emitGeneric(tripId, userId, type,
                        Map.of("actorName", notificationHelper.actorName(userId), "tripName", trip.getName()),
                        List.of(member.getUserId()), null);
            }
            tripRealtimePublisher.publishAfterCommit(
                    TripRealtimeEventType.MEMBER_REMOVED, tripId, memberId, userId);
            return;
        }

        tripMemberRepository.deleteById(memberId);
        log.info("Member removed from trip: {} - {}", tripId, memberId);

        if (member.getUserId() != null) {
            notificationHelper.emitGeneric(tripId, userId, NotificationType.MEMBER_REMOVED,
                    Map.of(
                            "actorName", notificationHelper.actorName(userId),
                            "removedMemberName", notificationHelper.actorName(member.getUserId()),
                            "tripName", trip.getName(),
                            "recipientContext", "target"
                    ), List.of(member.getUserId()), null);
        }
        notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.MEMBER_REMOVED,
                Map.of(
                        "actorName", notificationHelper.actorName(userId),
                        "removedMemberName", member.getUserId() != null
                                ? notificationHelper.actorName(member.getUserId()) : member.getGuestName(),
                        "tripName", trip.getName(),
                        "deepLink", "/trip/" + tripId + "/members"
                ), member.getUserId() == null ? List.of() : List.of(member.getUserId()));
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.MEMBER_REMOVED, tripId, memberId, userId);
    }

    @Override
    @Transactional
    public void updateMemberRole(UUID tripId, UUID memberId, String role, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only owner can update member role");
        }

        TripMember member = tripMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Member not found"));

        if (!tripId.equals(member.getTripId())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Member does not belong to this trip");
        }

        MemberRole newRole;
        try {
            newRole = MemberRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid member role");
        }
        if (newRole == MemberRole.OWNER) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Cannot assign owner role");
        }

        member.setRole(newRole);
        tripMemberRepository.updateById(member);
        log.info("Member role updated: {} - {} - {}", tripId, memberId, newRole);
        Map<String, Object> roleData = Map.of(
                "actorName", notificationHelper.actorName(userId),
                "memberName", member.getUserId() != null
                        ? notificationHelper.actorName(member.getUserId()) : member.getGuestName(),
                "role", newRole.name(),
                "tripName", trip.getName(),
                "deepLink", "/trip/" + tripId + "/members"
        );
        if (member.getUserId() != null) {
            notificationHelper.emitGeneric(tripId, userId, NotificationType.MEMBER_ROLE_UPDATED,
                    roleData, List.of(member.getUserId()), null);
        }
        notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.MEMBER_ROLE_UPDATED,
                roleData, member.getUserId() == null ? List.of() : List.of(member.getUserId()));
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.MEMBER_ROLE_UPDATED, tripId, memberId, userId);
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

        String previousGuestName = guestMember.getGuestName();
        guestMember.setGuestName(guestName);
        tripMemberRepository.updateById(guestMember);
        log.info("Guest name updated: {} - {} - {}", tripId, guestMemberId, guestName);
        notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.GUEST_UPDATED,
                Map.of(
                        "actorName", notificationHelper.actorName(userId),
                        "previousGuestName", previousGuestName,
                        "guestName", guestName,
                        "tripName", trip.getName(),
                        "deepLink", "/trip/" + tripId + "/members"
                ), null);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.MEMBER_GUEST_UPDATED, tripId, guestMemberId, userId);
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
        return mapToTripResponse(trip, userId, null, null);
    }

    /**
     * @param preloadedOwner   the owner row when the caller already holds it, so a list of trips
     *                         owned by one person does not load that person once per trip
     * @param membersByTripId  the viewer's member row per trip when the caller batch-loaded them;
     *                         {@code null} falls back to one lookup, an absent key means the
     *                         viewer is not a member of that trip
     */
    private TripResponse mapToTripResponse(Trip trip, UUID userId,
                                           User preloadedOwner,
                                           Map<UUID, TripMember> membersByTripId) {
        TripStatsResponse stats = calculateTripStats(trip);

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
            var member = membersByTripId != null
                    ? Optional.ofNullable(membersByTripId.get(trip.getId()))
                    : tripMemberRepository.findByTripIdAndUserId(trip.getId(), userId);
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

        List<MemoryImageResponse> memoryImages = getTripMemoryImages(trip.getId());
        List<String> memoryImageUrls = getMemoryImageUrls(memoryImages);
        List<TripDestinationResponse> destinationResponses = destinationResponses(trip);
        User owner;
        if (trip.getOwnerId() == null) {
            owner = null;
        } else if (preloadedOwner != null && trip.getOwnerId().equals(preloadedOwner.getId())) {
            owner = preloadedOwner;
        } else {
            owner = userRepository.findById(trip.getOwnerId()).orElse(null);
        }
        String ownerName = owner == null
                ? null
                : (owner.getFullName() != null && !owner.getFullName().isBlank()
                    ? owner.getFullName()
                    : owner.getUsername());

        return TripResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .coverImageUrl(coverImageUrl)
                .memoryImageUrls(memoryImageUrls)
                .memoryImageUrlsV2(memoryImages)
                .destination(trip.getDestination())
                .lat(trip.getDestinationLat())
                .lng(trip.getDestinationLng())
                .destinations(destinationResponses)
                .routeSummary(routeSummary(destinationResponses))
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
                .ownerId(trip.getOwnerId())
                .ownerName(ownerName)
                .ownerAvatarUrl(owner != null ? owner.getAvatarUrl() : null)
                .viewCount(trip.getViewCount())
                .copyCount(trip.getCopyCount())
                .helpfulVotes(trip.getHelpfulVotes() != null ? trip.getHelpfulVotes() : 0)
                .unhelpfulVotes(trip.getUnhelpfulVotes() != null ? trip.getUnhelpfulVotes() : 0)
                .publicSharedAt(trip.getPublicSharedAt())
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
                    .invitedBy(member.getInvitedBy())
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
                .invitedBy(member.getInvitedBy())
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
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        return calculateTripStats(trip);
    }

    /** Same stats for a caller that already holds the trip row, so it is not read twice. */
    private TripStatsResponse calculateTripStats(Trip trip) {
        UUID tripId = trip.getId();
        int totalItems = activityRepository.findByTripId(tripId).size();
        int checkedInItems = checkinRepository.findByTripId(tripId).size();
        int totalMembers = countAcceptedMembers(tripId);
        BigDecimal totalExpenses = expenseRepository.findByTripId(tripId).stream()
                .map(e -> e.getAmountInTripCurrency() != null ? e.getAmountInTripCurrency() : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
            Set<UUID> memberUserIds,
            UUID viewerId
    ) {
        List<UserReview> reviews = userReviewRepository.findByPlaceId(placeId, 1000, 0).stream()
                .filter(review -> memberUserIds.contains(review.getUserId()))
                .sorted(sharedTripReviewComparator(ownerId))
                .collect(Collectors.toList());

        return reviews.stream()
                .map(review -> mapToSharedTripUserReviewResponse(review, viewerId))
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

    private UserReviewResponse mapToSharedTripUserReviewResponse(UserReview review, UUID viewerId) {
        User user = userRepository.findById(review.getUserId()).orElse(null);
        UserReviewProfile profile = userReviewProfileRepository.findByUserId(review.getUserId()).orElse(null);
        List<MemoryImageResponse> photoResponses = MediaAssetResponseMapper.toImageResponses(
                mediaAssetRepository.findByEntity("USER_REVIEW", review.getId()));

        Boolean hasVotedHelpful = null;
        boolean isOwnReview = false;
        if (viewerId != null) {
            isOwnReview = review.getUserId().equals(viewerId);
            ReviewHelpfulVote vote = reviewHelpfulVoteRepository.findByReviewIdAndUserId(
                    review.getId(), viewerId);
            if (vote != null) {
                hasVotedHelpful = vote.isHelpful();
            }
        }

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
                .photos(photoResponses.isEmpty() ? null : MediaAssetResponseMapper.toUrls(photoResponses))
                .photosV2(photoResponses)
                .weight(review.getWeight() != null ? review.getWeight() : BigDecimal.ONE)
                .helpfulVotes(review.getHelpfulVotes() != null ? review.getHelpfulVotes() : 0)
                .unhelpfulVotes(review.getUnhelpfulVotes() != null ? review.getUnhelpfulVotes() : 0)
                .hasVotedHelpful(hasVotedHelpful)
                .isOwnReview(isOwnReview)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
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
                .filter(member -> member.getInvitedBy() != null)
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
                        log.error("Error processing pending invitation for member: {}, error: {}", member.getId(), e.getMessage(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull) // Filter out failed invitations
                .collect(Collectors.toList());
    }

    @Override
    public List<TripAccessRequestResponse> getPendingAccessRequests(UUID userId) {
        List<TripMember> pendingMembers = tripMemberRepository.findPendingByUserId(userId);

        return pendingMembers.stream()
                .filter(member -> member.getInvitedBy() == null)
                .map(member -> {
                    try {
                        Trip trip = tripRepository.findById(member.getTripId())
                                .orElse(null);

                        if (trip == null) {
                            log.warn("Trip not found for pending access request: tripId={}", member.getTripId());
                            return null;
                        }

                        String coverImageUrl = trip.getCoverImageUrl();
                        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
                            coverImageUrl = locationImageService.getImageForDestination(trip.getDestination());
                        }

                        return TripAccessRequestResponse.builder()
                                .memberId(member.getId())
                                .tripId(trip.getId())
                                .tripName(trip.getName())
                                .coverImageUrl(coverImageUrl)
                                .destination(trip.getDestination())
                                .startDate(trip.getStartDate())
                                .endDate(trip.getEndDate())
                                .role(member.getRole().toString())
                                .requestedAt(member.getCreatedAt())
                                .build();
                    } catch (Exception e) {
                        log.error("Error processing pending access request for member: {}, error: {}", member.getId(), e.getMessage(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
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

        // Validate current user is owner or editor. The plain member lookup used before this
        // ignored status and role, so a PENDING invitee or a VIEWER could link a guest away.
        tripAccessGuard.requireEditAccess(tripId, currentUserId);

        // Linking deletes the guest row. Splits survive that: they are moved onto the target
        // user below and stop referencing the guest at all, but an expense that names this
        // guest as its payer does not: expenses.paid_by keeps the member id while the FK on
        // paid_by_guest_member_id nulls itself out, leaving an expense paid by nobody and money
        // that is never shown as owed to the linked user. Nothing is silently moved or dropped:
        // the link is refused until those expenses name a different payer.
        int paidExpenses = expenseRepository.countByPaidByGuestMemberId(guestMemberId);
        if (paidExpenses > 0) {
            throw new BusinessException(
                    ErrorConstant.TRIP_GUEST_HAS_EXPENSES,
                    paidExpenses + " expense(s) in this trip are still recorded as paid by this "
                            + "guest. Change the payer on those expenses first, then link the guest.");
        }

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

        Map<String, Object> linkedData = Map.of(
                "guestName", guestMember.getGuestName(),
                "linkedUserName", notificationHelper.actorName(targetUserId),
                "tripName", notificationHelper.tripName(tripId),
                "deepLink", "/trip/" + tripId + "/members"
        );
        Map<String, Object> targetData = new HashMap<>(linkedData);
        targetData.put("recipientContext", "target");
        notificationHelper.emitGeneric(tripId, currentUserId, NotificationType.GUEST_LINKED,
                targetData, List.of(targetUserId), null);
        notificationHelper.emitGenericToMembers(tripId, currentUserId, NotificationType.GUEST_LINKED,
                linkedData, List.of(targetUserId));
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.MEMBER_REMOVED, tripId, guestMemberId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicTripResponse getPublicTrip(UUID tripId, UUID viewerId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!canViewPublicTrip(trip, viewerId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Trip is not shared");
        }

        // Increment view count async (don't block response)
        incrementViewCountAsync(tripId);

        // Auto fill cover image if not set
        String coverImageUrl = trip.getCoverImageUrl();
        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
            coverImageUrl = locationImageService.getImageForDestination(trip.getDestination());
        }
        List<MemoryImageResponse> memoryImages = getTripMemoryImages(tripId);
        List<String> memoryImageUrls = getMemoryImageUrls(memoryImages);

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
            Map<UUID, List<MediaAsset>> expenseAssets = mediaAssetRepository
                    .findByEntityIds("EXPENSE", expenses.stream().map(Expense::getId).toList())
                    .stream()
                    .filter(asset -> asset.getAssetRole() == null
                            || "PHOTO".equalsIgnoreCase(asset.getAssetRole()))
                    .collect(Collectors.groupingBy(MediaAsset::getEntityId));
            // One grouped count for the trip rather than one query per expense: this page is
            // reachable without signing in, so the query count is whatever a stranger asks for.
            Map<UUID, Integer> splitCounts = expenseRepository.findSplitCountsByTripId(tripId).stream()
                    .collect(Collectors.toMap(ExpenseSplitCount::getExpenseId,
                            count -> count.getSplitCount() == null ? 0 : count.getSplitCount(),
                            (first, second) -> first));
            for (com.ds.goroute.entity.Expense e : expenses) {
                int splitCount = splitCounts.getOrDefault(e.getId(), 0);
                List<MemoryImageResponse> expensePhotoResponses = MediaAssetResponseMapper.toImageResponses(
                        expenseAssets.getOrDefault(e.getId(), List.of()));
                PublicExpenseResponse expenseResponse = PublicExpenseResponse.builder()
                        .id(e.getId())
                        .amount(e.getAmount())
                        .currency(e.getCurrency())
                        .category(String.valueOf(e.getCategory()))
                        .description(e.getDescription())
                        .splitCount(splitCount)
                        .photoUrls(MediaAssetResponseMapper.toUrls(expensePhotoResponses))
                        .photoUrlsV2(expensePhotoResponses)
                        // A shared trip shows the day of the spend, not the day it was typed in.
                        .createdAt(e.getExpenseDate() != null ? e.getExpenseDate() : e.getCreatedAt())
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
                    List<MemoryImageResponse> activityMemoryImages = getActivityMemoryImages(a.getId(), a.getName());
                    List<UserReviewResponse> memberReviews = placeId != null
                            ? buildSharedTripReviews(placeId, trip.getOwnerId(), memberUserIds, viewerId)
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
                            .memoryImageUrls(getMemoryImageUrls(activityMemoryImages))
                            .memoryImageUrlsV2(activityMemoryImages)
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
        List<TripDestinationResponse> destinationResponses = destinationResponses(trip);

        return PublicTripResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .coverImageUrl(coverImageUrl)
                .memoryImageUrls(memoryImageUrls)
                .memoryImageUrlsV2(memoryImages)
                .description(trip.getDescription())
                .destination(trip.getDestination())
                .lat(trip.getDestinationLat())
                .lng(trip.getDestinationLng())
                .destinations(destinationResponses)
                .routeSummary(routeSummary(destinationResponses))
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .currency(trip.getCurrency())
                .ownerId(trip.getOwnerId())
                .ownerName(ownerName)
                .ownerAvatarUrl(ownerAvatarUrl)
                .activities(activityResponses)
                .expenses(tripLevelExpenses.isEmpty() ? null : tripLevelExpenses)
                .notes(tripLevelNotes.isEmpty() ? null : tripLevelNotes)
                .viewCount(trip.getViewCount())
                .copyCount(trip.getCopyCount())
                .helpfulVotes(trip.getHelpfulVotes() != null ? trip.getHelpfulVotes() : 0)
                .unhelpfulVotes(trip.getUnhelpfulVotes() != null ? trip.getUnhelpfulVotes() : 0)
                .hasVotedHelpful(resolveTripHasVotedHelpful(trip.getId(), viewerId))
                .isOwnTrip(viewerId != null && trip.getOwnerId().equals(viewerId))
                .totalMembers(countAcceptedMembers(trip.getId()))
                .totalActivities(activityResponses != null ? activityResponses.size() : 0)
                .publicSharedAt(trip.getPublicSharedAt())
                .build();
    }

    /**
     * Whether {@code viewerId} may read this trip through the anonymous public endpoint.
     *
     * <p>PUBLIC is open to anyone. SHARED means "members only", not "anyone with the
     * link" -- it must never fall through to open access just because it is not PRIVATE,
     * which is the mistake this method exists to make impossible to repeat. PRIVATE is
     * never viewable here at all.
     */
    boolean canViewPublicTrip(Trip trip, UUID viewerId) {
        return switch (trip.getVisibility()) {
            case PUBLIC -> true;
            case SHARED -> viewerId != null
                    && (trip.getOwnerId().equals(viewerId)
                            || tripMemberRepository.findByTripIdAndUserId(trip.getId(), viewerId)
                                    .filter(member -> member.getStatus() == MemberStatus.ACCEPTED)
                                    .isPresent());
            case PRIVATE -> false;
        };
    }

    @CacheEvict(cacheNames = "publicTripSearch", cacheManager = "searchCacheManager", allEntries = true)
    @Override
    @Transactional
    public TripVoteResponse voteTripHelpful(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        ensurePublicTripVotable(trip);

        TripHelpfulVote existingVote = tripHelpfulVoteRepository.findByTripIdAndUserId(tripId, userId);
        boolean isHelpfulNow = true;
        if (existingVote != null) {
            if (existingVote.isHelpful()) {
                tripHelpfulVoteRepository.delete(tripId, userId);
                isHelpfulNow = false;
            } else {
                existingVote.setHelpful(true);
                tripHelpfulVoteRepository.update(existingVote);
            }
        } else {
            tripHelpfulVoteRepository.save(TripHelpfulVote.builder()
                    .tripId(tripId)
                    .userId(userId)
                    .isHelpful(true)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        syncTripVoteCounts(trip);
        if (isHelpfulNow) {
            socialNotificationService.notifyLike(
                    trip.getOwnerId(), userId, "TRIP", tripId);
        }
        return buildTripVoteResponse(trip, userId);
    }

    @CacheEvict(cacheNames = "publicTripSearch", cacheManager = "searchCacheManager", allEntries = true)
    @Override
    @Transactional
    public TripVoteResponse voteTripUnhelpful(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        ensurePublicTripVotable(trip);

        TripHelpfulVote existingVote = tripHelpfulVoteRepository.findByTripIdAndUserId(tripId, userId);
        if (existingVote != null) {
            if (!existingVote.isHelpful()) {
                tripHelpfulVoteRepository.delete(tripId, userId);
            } else {
                existingVote.setHelpful(false);
                tripHelpfulVoteRepository.update(existingVote);
            }
        } else {
            tripHelpfulVoteRepository.save(TripHelpfulVote.builder()
                    .tripId(tripId)
                    .userId(userId)
                    .isHelpful(false)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        syncTripVoteCounts(trip);
        return buildTripVoteResponse(trip, userId);
    }

    private TripVoteResponse buildTripVoteResponse(Trip trip, UUID userId) {
        return TripVoteResponse.builder()
                .tripId(trip.getId())
                .helpfulVotes(trip.getHelpfulVotes() != null ? trip.getHelpfulVotes() : 0)
                .unhelpfulVotes(trip.getUnhelpfulVotes() != null ? trip.getUnhelpfulVotes() : 0)
                .hasVotedHelpful(resolveTripHasVotedHelpful(trip.getId(), userId))
                .build();
    }

    private void ensurePublicTripVotable(Trip trip) {
        if (trip.getVisibility() == TripVisibility.PRIVATE) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Trip is not shared");
        }
    }

    private void syncTripVoteCounts(Trip trip) {
        int helpfulCount = tripHelpfulVoteRepository.countByTripIdAndIsHelpful(trip.getId(), true);
        int unhelpfulCount = tripHelpfulVoteRepository.countByTripIdAndIsHelpful(trip.getId(), false);
        trip.setHelpfulVotes(helpfulCount);
        trip.setUnhelpfulVotes(unhelpfulCount);
        tripRepository.updateVoteCounts(trip);
    }

    private Boolean resolveTripHasVotedHelpful(UUID tripId, UUID viewerId) {
        if (viewerId == null) {
            return null;
        }
        TripHelpfulVote vote = tripHelpfulVoteRepository.findByTripIdAndUserId(tripId, viewerId);
        if (vote == null) {
            return null;
        }
        return vote.isHelpful();
    }

    @Override
    @Transactional
    public TripResponse joinTripByCode(String code, UUID userId) {
        Trip trip = tripRepository.findByShareCode(code)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found with code: " + code));

        // Check if user is already an active/pending member. A declined or left
        // membership is reusable because trip_members has a unique user/trip key.
        var existingMember = tripMemberRepository.findByTripIdAndUserId(trip.getId(), userId);
        TripMember member;
        if (existingMember.isPresent()) {
            member = existingMember.get();
            if (!isReactivatableMember(member)) {
                throw new BusinessException(ErrorConstant.TRIP_MEMBER_ALREADY_EXISTS);
            }

            // A code join is an owner approval request, not an invitation. Clear
            // invitedBy so acceptMember() can distinguish the two workflows.
            member.setRole(MemberRole.VIEWER);
            member.setStatus(MemberStatus.PENDING);
            member.setInvitedBy(null);
            member.setJoinedAt(null);
            member.setCreatedAt(LocalDateTime.now());
            member.setGuestName(null);
            member.setIsGuest(false);
            tripMemberRepository.updateById(member);
        } else {
            member = TripMember.builder()
                    .id(UUID.randomUUID())
                    .tripId(trip.getId())
                    .userId(userId)
                    .role(MemberRole.VIEWER)
                    .status(MemberStatus.PENDING)
                    .build();

            tripMemberRepository.insert(member);
        }
        log.info("User joined trip by code: tripId={}, userId={}, code={}", trip.getId(), userId, code);

        notificationHelper.emitGeneric(trip.getId(), userId, NotificationType.MEMBER_JOIN_REQUESTED,
                Map.of(
                        "memberName", notificationHelper.actorName(userId),
                        "tripName", trip.getName(),
                        "deepLink", "/trip/" + trip.getId() + "/members"
                ), List.of(trip.getOwnerId()), null);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.MEMBER_INVITED, trip.getId(), member.getId(), userId);

        return mapToTripResponse(trip, userId);
    }

    private boolean isReactivatableMember(TripMember member) {
        return member.getStatus() == MemberStatus.DECLINED
                || member.getStatus() == MemberStatus.LEFT;
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

        if (member.getInvitedBy() != null) {
            throw new BusinessException(
                    ErrorConstant.INVALID_PARAMETERS,
                    "Invited members must accept the invitation themselves"
            );
        }

        member.setStatus(MemberStatus.ACCEPTED);
        member.setJoinedAt(LocalDateTime.now());
        tripMemberRepository.updateById(member);
        log.info("Member accepted: tripId={}, memberId={}, acceptedBy={}", tripId, memberId, userId);

        notificationHelper.emitGeneric(tripId, userId, NotificationType.MEMBER_ACCESS_GRANTED,
                Map.of(
                        "actorName", notificationHelper.actorName(userId),
                        "tripName", trip.getName(),
                        "deepLink", "/trip/" + tripId
                ), List.of(member.getUserId()), null);
        notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.MEMBER_ACCEPTED,
                Map.of(
                        "memberName", notificationHelper.actorName(member.getUserId()),
                        "tripName", trip.getName(),
                        "deepLink", "/trip/" + tripId + "/members"
                ), List.of(member.getUserId()));
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.MEMBER_ACCEPTED, tripId, memberId, userId);
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
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.MEMBER_REMOVED, tripId, member.getId(), userId);
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

        // 4. A clone is a trip, so it costs what a trip costs. Left out, this was the way
        // past the quota: copy somebody's public trip instead of starting one. Reserved
        // after the access check, so a clone the user is not allowed to make cannot spend
        // a slot on its way to being refused.
        starService.reserveTripCreation(userId);

        // 5. Create new trip with user-provided info
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
        List<TripDestination> clonedDestinations = cloneDestinationsForTrip(
                originalTrip,
                newTrip.getId(),
                request.getStartDate(),
                request.getEndDate()
        );
        clonedDestinations.forEach(tripDestinationRepository::insert);
        log.info("Cloned trip created: originalTripId={}, newTripId={}, userId={}", tripId, newTrip.getId(), userId);
        starService.grant(originalTrip.getOwnerId(), 2, "TRIP_CLONED",
                "clone:" + tripId + ":" + userId, "Someone cloned your trip");

        // 6. Add user as owner member
        TripMember owner = TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(newTrip.getId())
                .userId(userId)
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACCEPTED)
                .joinedAt(LocalDateTime.now())
                .build();
        tripMemberRepository.insert(owner);

        // 7. Increment copy count of original trip
        try {
            tripRepository.incrementCopyCount(tripId);
        } catch (Exception e) {
            log.warn("Failed to increment copy count for trip: {}", tripId, e);
        }

        // 8. Clone activities (without expenses)
        List<Activity> originalActivities = activityRepository.findByTripId(tripId);
        for (Activity originalActivity : originalActivities) {
            Activity newActivity = Activity.builder()
                    .id(UUID.randomUUID())
                    .tripId(newTrip.getId())
                    .dayNumber(originalActivity.getDayNumber())
                    .sortOrder(originalActivity.getSortOrder())
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
                    .endDayNumber(originalActivity.getEndDayNumber())
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

        if (!originalTrip.getOwnerId().equals(userId)) {
            notificationHelper.emitGeneric(tripId, userId, NotificationType.TRIP_CLONED,
                    Map.of(
                            "actorName", notificationHelper.actorName(userId),
                            "tripName", originalTrip.getName(),
                            "deepLink", "/tour/trip/" + tripId
                    ), List.of(originalTrip.getOwnerId()), null);
        }

        // 9. Return response
        return mapToTripResponse(newTrip, userId);
    }

    @Cacheable(
            cacheNames = "publicTripSearch",
            cacheManager = "searchCacheManager",
            keyGenerator = "searchCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()")
    @Override
    @Transactional(readOnly = true)
    public List<PublicTripResponse> searchPublicTrips(BigDecimal latitude,
                                                       BigDecimal longitude,
                                                       BigDecimal radiusKm,
                                                       String destination,
                                                       String keyword,
                                                       boolean allPublic,
                                                       String randomSeed,
                                                       boolean preferImages,
                                                       int page,
                                                       int size,
                                                       UUID viewerId,
                                                       UUID excludeUserId) {
        List<Trip> trips = tripRepository.searchPublicTrips(
                latitude,
                longitude,
                radiusKm,
                destination,
                keyword,
                allPublic,
                randomSeed,
                preferImages,
                page,
                size,
                excludeUserId
        );

        return trips.stream()
                .map(trip -> {
                    String coverImageUrl = trip.getCoverImageUrl();
                    if (coverImageUrl == null || coverImageUrl.isEmpty()) {
                        coverImageUrl = locationImageService.getImageForDestination(trip.getDestination());
                    }
                    List<MemoryImageResponse> memoryImages = getTripMemoryImages(trip.getId());
                    List<String> memoryImageUrls = getMemoryImageUrls(memoryImages);

                    // Get owner info
                    User owner = userRepository.findById(trip.getOwnerId()).orElse(null);
                    String ownerName = owner != null ? owner.getFullName() : null;
                    String ownerAvatarUrl = owner != null ? owner.getAvatarUrl() : null;
                    List<TripDestinationResponse> destinationResponses = destinationResponses(trip);

                    return PublicTripResponse.builder()
                            .id(trip.getId())
                            .name(trip.getName())
                            .coverImageUrl(coverImageUrl)
                            .memoryImageUrls(memoryImageUrls)
                            .memoryImageUrlsV2(memoryImages)
                            .description(trip.getDescription())
                            .destination(trip.getDestination())
                            .lat(trip.getDestinationLat())
                            .lng(trip.getDestinationLng())
                            .destinations(destinationResponses)
                            .routeSummary(routeSummary(destinationResponses))
                            .startDate(trip.getStartDate())
                            .endDate(trip.getEndDate())
                            .currency(trip.getCurrency())
                            .ownerId(trip.getOwnerId())
                            .ownerName(ownerName)
                            .ownerAvatarUrl(ownerAvatarUrl)
                            .activities(null)
                            .expenses(null)
                            .notes(null)
                            .viewCount(trip.getViewCount())
                            .copyCount(trip.getCopyCount())
                            .helpfulVotes(trip.getHelpfulVotes() != null ? trip.getHelpfulVotes() : 0)
                            .unhelpfulVotes(trip.getUnhelpfulVotes() != null ? trip.getUnhelpfulVotes() : 0)
                            .hasVotedHelpful(resolveTripHasVotedHelpful(trip.getId(), viewerId))
                            .isOwnTrip(viewerId != null && trip.getOwnerId().equals(viewerId))
                            .totalMembers(countAcceptedMembers(trip.getId()))
                            .totalActivities(activityRepository.countByTripId(trip.getId()))
                            .publicSharedAt(trip.getPublicSharedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private int countAcceptedMembers(UUID tripId) {
        return (int) tripMemberRepository.findByTripId(tripId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACCEPTED)
                .count();
    }

    private List<MemoryImageResponse> getTripMemoryImages(UUID tripId) {
        List<MediaAsset> assets = mediaAssetRepository.findTripMemoriesByTripId(tripId);
        return toMemoryImageResponses(assets, activityNamesForTripPhotos(tripId, assets));
    }

    /**
     * @param activityName already in hand at every call site, so naming the stop
     *                     costs nothing here
     */
    private List<MemoryImageResponse> getActivityMemoryImages(UUID activityId, String activityName) {
        List<MediaAsset> assets = mediaAssetRepository.findTripMemoriesByActivityId(activityId);
        Map<UUID, String> names = activityName == null || activityName.isBlank()
                ? Map.of()
                : Map.of(activityId, activityName);
        return toMemoryImageResponses(assets, names);
    }

    private List<MemoryImageResponse> toMemoryImageResponses(List<MediaAsset> assets,
                                                             Map<UUID, String> activityNames) {
        return MediaAssetResponseMapper.toImageResponses(assets, uploadersFor(assets), activityNames);
    }

    /**
     * The users behind a set of photos, in one query rather than one per photo.
     *
     * <p>A trip's memories usually come from a handful of members, so the map is
     * small even when the gallery is long.
     */
    private Map<UUID, User> uploadersFor(List<MediaAsset> assets) {
        Set<UUID> uploaderIds = assets.stream()
                .map(MediaAsset::getUploadedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (uploaderIds.isEmpty()) return Map.of();
        return userRepository.findByIds(uploaderIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first));
    }

    /**
     * Names for the activities a trip's photos are attached to.
     *
     * <p>Trip-level memories carry no activity, so a gallery of only those skips
     * the query entirely.
     */
    private Map<UUID, String> activityNamesForTripPhotos(UUID tripId, List<MediaAsset> assets) {
        boolean anyAttachedToActivity = assets.stream().anyMatch(asset -> asset.getActivityId() != null);
        if (!anyAttachedToActivity) return Map.of();

        Map<UUID, String> names = new HashMap<>();
        for (Activity activity : activityRepository.findByTripId(tripId)) {
            if (activity.getName() != null) {
                names.put(activity.getId(), activity.getName());
            }
        }
        return names;
    }

    private List<String> getMemoryImageUrls(List<MemoryImageResponse> images) {
        return MediaAssetResponseMapper.toUrls(images);
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

    @Override
    @Transactional(readOnly = true)
    public TripSearchBiasResponse resolveSearchBias(UUID tripId, int dayNumber, UUID userId, UUID overrideDestinationId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        ensureTripAccess(trip, userId);

        if (overrideDestinationId != null) {
            Optional<TripDestination> override = tripDestinationRepository.findById(overrideDestinationId)
                    .filter(destination -> tripId.equals(destination.getTripId()));
            if (override.isPresent() && hasCoordinates(override.get())) {
                TripDestination destination = override.get();
                return TripSearchBiasResponse.builder()
                        .lat(destination.getLat())
                        .lng(destination.getLng())
                        .source("USER_OVERRIDE")
                        .label("User selected search area")
                        .destinationId(destination.getId())
                        .destinationName(destination.getName())
                        .build();
            }
        }

        Optional<Activity> currentDayLast = latestActivityWithCoordinates(tripId, dayNumber);
        if (currentDayLast.isPresent()) {
            Activity activity = currentDayLast.get();
            return TripSearchBiasResponse.builder()
                    .lat(activity.getLat())
                    .lng(activity.getLng())
                    .source("CURRENT_DAY_LAST_ITEM")
                    .label("Last item on this day")
                    .build();
        }

        for (int previousDay = dayNumber - 1; previousDay >= 1; previousDay--) {
            Optional<Activity> previousDayLast = latestActivityWithCoordinates(tripId, previousDay);
            if (previousDayLast.isPresent()) {
                Activity activity = previousDayLast.get();
                return TripSearchBiasResponse.builder()
                        .lat(activity.getLat())
                        .lng(activity.getLng())
                        .source("PREVIOUS_DAY_LAST_ITEM")
                        .label("Last item from previous day")
                        .build();
            }
        }

        LocalDate targetDate = trip.getStartDate().plusDays(Math.max(0, dayNumber - 1));
        List<TripDestination> matchingDestinations = tripDestinationRepository.findByTripId(tripId).stream()
                .filter(this::hasCoordinates)
                .filter(destination -> containsDate(destination, targetDate))
                .sorted(Comparator.comparing(TripDestination::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        if (matchingDestinations.size() == 1) {
            TripDestination destination = matchingDestinations.get(0);
            return TripSearchBiasResponse.builder()
                    .lat(destination.getLat())
                    .lng(destination.getLng())
                    .source("DESTINATION_DATE_RANGE")
                    .label("Trip area for this day")
                    .destinationId(destination.getId())
                    .destinationName(destination.getName())
                    .build();
        }
        if (matchingDestinations.size() > 1) {
            TripDestination destination = matchingDestinations.get(0);
            return TripSearchBiasResponse.builder()
                    .lat(destination.getLat())
                    .lng(destination.getLng())
                    .source("DESTINATION_OVERLAP_DEFAULT")
                    .label("Default area for overlapping destination day")
                    .destinationId(destination.getId())
                    .destinationName(destination.getName())
                    .build();
        }

        if (trip.getDestinationLat() != null && trip.getDestinationLng() != null) {
            return TripSearchBiasResponse.builder()
                    .lat(trip.getDestinationLat())
                    .lng(trip.getDestinationLng())
                    .source("TRIP_CENTER")
                    .label("Primary trip area")
                    .destinationName(trip.getDestination())
                    .build();
        }

        return TripSearchBiasResponse.builder()
                .source("NONE")
                .label("Search without location bias")
                .build();
    }

    private void ensureTripAccess(Trip trip, UUID userId) {
        var member = tripMemberRepository.findByTripIdAndUserId(trip.getId(), userId);
        boolean hasAccess = trip.getOwnerId().equals(userId) ||
                (member.isPresent() && member.get().getStatus() == MemberStatus.ACCEPTED);
        if (!hasAccess) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }
    }

    private Optional<Activity> latestActivityWithCoordinates(UUID tripId, int dayNumber) {
        List<Activity> activities = activityRepository.findByTripIdAndDayNumber(tripId, dayNumber).stream()
                .filter(activity -> activity.getLat() != null && activity.getLng() != null)
                .toList();
        if (activities.isEmpty()) {
            return Optional.empty();
        }

        List<Activity> withTime = activities.stream()
                .filter(activity -> activity.getStartTime() != null)
                .toList();
        if (!withTime.isEmpty()) {
            return withTime.stream().max(
                    Comparator.comparing(Activity::getStartTime)
                            .thenComparing(Activity::getSortOrder, Comparator.nullsFirst(Integer::compareTo))
                            .thenComparing(Activity::getCreatedAt, Comparator.nullsFirst(LocalDateTime::compareTo))
            );
        }

        return activities.stream().max(
                Comparator.comparing(Activity::getSortOrder, Comparator.nullsFirst(Integer::compareTo))
                        .thenComparing(Activity::getCreatedAt, Comparator.nullsFirst(LocalDateTime::compareTo))
        );
    }

    private boolean hasCoordinates(TripDestination destination) {
        return destination.getLat() != null && destination.getLng() != null;
    }

    private boolean containsDate(TripDestination destination, LocalDate date) {
        if (destination.getStartDate() == null || destination.getEndDate() == null || date == null) {
            return false;
        }
        return !date.isBefore(destination.getStartDate()) && !date.isAfter(destination.getEndDate());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripResponse> getProfileTrips(UUID targetUserId, UUID viewerId) {
        User profileOwner = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        boolean isSelf = viewerId != null && viewerId.equals(targetUserId);
        List<Trip> trips = isSelf
                ? tripRepository.findByOwnerId(targetUserId)
                : tripRepository.findPublicTripsByOwnerId(targetUserId);

        UUID mapViewerId = viewerId != null ? viewerId : targetUserId;

        // Every trip here is owned by the profile's user, and the viewer is one person: both
        // lookups are the same row over and over. A signed-in stranger opening a profile used
        // to cost two queries per trip before the trip itself was read.
        Map<UUID, TripMember> viewerMembership = mapViewerId.equals(targetUserId)
                ? Map.of()
                : tripMemberRepository
                        .findByTripIdsAndUserId(trips.stream().map(Trip::getId).toList(), mapViewerId)
                        .stream()
                        .collect(Collectors.toMap(TripMember::getTripId, member -> member,
                                (first, second) -> first));

        return trips.stream()
                .map(trip -> mapToTripResponse(trip, mapViewerId, profileOwner, viewerMembership))
                .collect(Collectors.toList());
    }

    /**
     * Fire-and-forget so the detail response is not held up by the counter write.
     * Runs on the shared bounded pool: a raw {@code new Thread()} here meant one OS
     * thread per view on a publicly reachable endpoint.
     */
    private void incrementViewCountAsync(UUID tripId) {
        applicationTaskExecutor.execute(() -> {
            try {
                tripRepository.incrementViewCount(tripId);
            } catch (Exception e) {
                log.warn("Failed to increment view count for trip: {}", tripId, e);
            }
        });
    }
}

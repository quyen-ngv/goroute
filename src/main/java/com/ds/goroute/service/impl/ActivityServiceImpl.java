package com.ds.goroute.service.impl;

import com.ds.goroute.service.TripAccessGuard;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.request.ReorderActivitiesRequest;
import com.ds.goroute.dto.request.UpdateActivityRequest;
import com.ds.goroute.dto.response.ActivityPlaceSummaryResponse;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.dto.response.ExpenseSplitResponse;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.dto.response.MemoryImageResponse;
import com.ds.goroute.utils.MediaAssetResponseMapper;
import com.ds.goroute.service.redis.RedisService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.ActivityStatus;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.TripRealtimeEventType;
import com.ds.goroute.type.TransportMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityServiceImpl implements ActivityService {

    private final ActivityRepository activityRepository;
    private final TripAccessGuard tripAccessGuard;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final CheckinRepository checkinRepository;
    private final RedisService redisService;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final PlaceRepository placeRepository;
    private final NotificationHelper notificationHelper;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final MediaAssetRepository mediaAssetRepository;
    private final TripRealtimePublisher tripRealtimePublisher;

    @Override
    @Transactional
    public ActivityResponse createActivity(UUID tripId, CreateActivityRequest request, UUID userId) {
        tripAccessGuard.requireEditAccess(tripId, userId);

        Activity activity = Activity.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .dayNumber(request.getDayNumber())
                .placeId(request.getPlaceId())
                .customPlaceId(request.getCustomPlaceId())
                .name(request.getName())
                .address(request.getAddress())
                .lat(request.getLat())
                .lng(request.getLng())
                .endLat(request.getEndLat())
                .endLng(request.getEndLng())
                .endAddress(request.getEndAddress())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .endDayNumber(request.getEndDayNumber())
                .estimatedCost(request.getEstimatedCost())
                .costCurrency(request.getCostCurrency())
                .category(request.getCategory())
                .transportMode(request.getTransportMode() != null ? TransportMode.valueOf(request.getTransportMode()) : null)
                .notes(request.getNotes())
                .description(request.getDescription())
                .status(ActivityStatus.CONFIRMED)
                .isAccommodation(request.getIsAccommodation() != null ? request.getIsAccommodation() : false)
                .isStartingPoint(request.getIsStartingPoint() != null ? request.getIsStartingPoint() : false)
                .startingPointDate(request.getStartingPointDate())
                .bookingId(request.getBookingId())
                .bookingSource(request.getBookingSource())
                .addedBy(userId)
                .build();

        activityRepository.insert(activity);
        log.info("Activity created: {} in trip: {}", activity.getId(), tripId);

        notificationHelper.emitActivityCreated(activity, userId);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.ACTIVITY_CREATED, tripId, activity.getId(), userId);

        return mapToActivityResponse(activity, findLinkedPlace(activity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityResponse> getActivities(UUID tripId, Integer dayNumber, UUID userId) {
        requireTripAccess(tripId, userId);
        List<Activity> activities;
        if (dayNumber != null) {
            activities = activityRepository.findByTripIdAndDayNumber(tripId, dayNumber);
        } else {
            activities = activityRepository.findByTripId(tripId);
        }

        Map<UUID, Place> placesById = placeRepository.findByIds(
                        activities.stream()
                                .map(this::linkedInternalPlaceId)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(Place::getId, place -> place, (left, right) -> left));

        Map<String, Place> placesByExternalId = placeRepository.findByPlaceIds(
                        activities.stream()
                                .map(Activity::getPlaceId)
                                .filter(this::isNonBlank)
                                .filter(placeId -> parseUuid(placeId) == null)
                                .distinct()
                                .toList())
                .stream()
                .filter(place -> isNonBlank(place.getPlaceId()))
                .collect(Collectors.toMap(Place::getPlaceId, place -> place, (left, right) -> left));

        return activities.stream()
                .map(activity -> mapToActivityResponse(
                        activity,
                        resolveLinkedPlace(activity, placesById, placesByExternalId)))
                .collect(Collectors.toList());
    }

    private Trip requireTripAccess(UUID tripId, UUID userId) {
        return tripAccessGuard.requireAccess(tripId, userId);
    }

    @Override
    @Transactional
    public ActivityResponse updateActivity(UUID tripId, UUID activityId, UpdateActivityRequest request, UUID userId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));

        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found");
        }

        tripAccessGuard.requireEditAccess(tripId, userId);

        if (request.getPlaceId() != null) activity.setPlaceId(request.getPlaceId());
        if (request.getCustomPlaceId() != null) activity.setCustomPlaceId(request.getCustomPlaceId());
        if (request.getName() != null) activity.setName(request.getName());
        if (request.getAddress() != null) activity.setAddress(request.getAddress());
        if (request.getLat() != null) activity.setLat(request.getLat());
        if (request.getLng() != null) activity.setLng(request.getLng());
        if (request.getEndLat() != null) activity.setEndLat(request.getEndLat());
        if (request.getEndLng() != null) activity.setEndLng(request.getEndLng());
        if (request.getEndAddress() != null) activity.setEndAddress(request.getEndAddress());
        if (request.getDayNumber() != null) activity.setDayNumber(request.getDayNumber());
        if (request.getStartTime() != null) activity.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) activity.setEndTime(request.getEndTime());
        if (request.getEndDayNumber() != null) activity.setEndDayNumber(request.getEndDayNumber());
        if (request.getEstimatedCost() != null) activity.setEstimatedCost(request.getEstimatedCost());
        if (request.getCostCurrency() != null) activity.setCostCurrency(request.getCostCurrency());
        if (request.getCategory() != null) activity.setCategory(request.getCategory());
        if (request.getTransportMode() != null) activity.setTransportMode(TransportMode.valueOf(request.getTransportMode()));
        if (request.getDistanceToNext() != null) activity.setDistanceToNext(request.getDistanceToNext());
        if (request.getDurationToNext() != null) activity.setDurationToNext(request.getDurationToNext());
        if (request.getDistanceValueToNext() != null) activity.setDistanceValueToNext(request.getDistanceValueToNext());
        if (request.getDurationValueToNext() != null) activity.setDurationValueToNext(request.getDurationValueToNext());
        if (request.getNotes() != null) activity.setNotes(request.getNotes());
        if (request.getDescription() != null) activity.setDescription(request.getDescription());
        if (request.getIsAccommodation() != null) activity.setIsAccommodation(request.getIsAccommodation());
        if (request.getIsStartingPoint() != null) activity.setIsStartingPoint(request.getIsStartingPoint());
        if (request.getStartingPointDate() != null) activity.setStartingPointDate(request.getStartingPointDate());

        activityRepository.updateById(activity);

        String cacheKey = "activities:" + tripId;
        redisService.delete(cacheKey);

        log.info("Activity updated: {}", activityId);

        notificationHelper.emitActivityUpdated(activity, userId);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.ACTIVITY_UPDATED, tripId, activityId, userId);

        return mapToActivityResponse(activity, findLinkedPlace(activity));
    }

    @Override
    @Transactional
    public void deleteActivity(UUID tripId, UUID activityId, UUID userId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));

        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found");
        }

        tripAccessGuard.requireEditAccess(tripId, userId);

        imageStorageCleanupService.deleteImagesForEntityRecord("ACTIVITY", activityId);
        activityRepository.deleteById(activityId);

        String cacheKey = "activities:" + tripId;
        redisService.delete(cacheKey);

        log.info("Activity deleted: {}", activityId);

        notificationHelper.emitActivityDeleted(activity, userId);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.ACTIVITY_DELETED, tripId, activityId, userId);
    }

    @Override
    @Transactional
    public void reorderActivities(UUID tripId, ReorderActivitiesRequest request, UUID userId) {
        tripAccessGuard.requireEditAccess(tripId, userId);

        // Reorder is now based on time, so this endpoint is deprecated
        // But we keep it for backward compatibility
        log.info("Activities reorder requested in trip: {} (deprecated - order by time)", tripId);
        tripRealtimePublisher.publishAfterCommit(
                TripRealtimeEventType.ACTIVITY_REORDERED, tripId, null, userId);
    }

    private ActivityResponse mapToActivityResponse(Activity activity, Place place) {
        int checkedInCount = checkinRepository.findByActivityId(activity.getId()).size();

        // Calculate actual spent from expenses
        List<Expense> expenses = expenseRepository.findByActivityId(activity.getId());
        BigDecimal actualSpent = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, List<MediaAsset>> assetsByExpense = mediaAssetRepository
                .findByEntityIds("EXPENSE", expenses.stream().map(Expense::getId).toList())
                .stream()
                .filter(asset -> asset.getAssetRole() == null
                        || "PHOTO".equalsIgnoreCase(asset.getAssetRole()))
                .collect(Collectors.groupingBy(MediaAsset::getEntityId));

        // Map expenses to response
        List<ExpenseResponse> expenseResponses = expenses.stream()
                .map(expense -> mapToExpenseResponse(
                        expense, assetsByExpense.getOrDefault(expense.getId(), List.of())))
                .collect(Collectors.toList());

        String address = firstNonBlank(place != null ? place.getAddress() : null, activity.getAddress());
        BigDecimal latitude = place != null && place.getLatitude() != null
                ? place.getLatitude()
                : activity.getLat();
        BigDecimal longitude = place != null && place.getLongitude() != null
                ? place.getLongitude()
                : activity.getLng();
        BigDecimal rating = placeRating(place, activity.getRating());
        String photoUrl = firstNonBlank(place != null ? place.getThumbnail() : null, activity.getPhotoUrl());

        return ActivityResponse.builder()
                .id(activity.getId())
                .tripId(activity.getTripId())
                .dayNumber(activity.getDayNumber())
                .placeId(activity.getPlaceId())
                .customPlaceId(activity.getCustomPlaceId())
                .placeRefId(activity.getPlaceRefId())
                .place(mapPlaceSummary(place))
                .name(activity.getName())
                .address(address)
                .lat(latitude)
                .lng(longitude)
                .endLat(activity.getEndLat())
                .endLng(activity.getEndLng())
                .endAddress(activity.getEndAddress())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .endDayNumber(activity.getEndDayNumber())
                .estimatedCost(activity.getEstimatedCost())
                .costCurrency(activity.getCostCurrency())
                .category(activity.getCategory())
                .transportMode(activity.getTransportMode() != null ? activity.getTransportMode().toString() : null)
                .distanceToNext(activity.getDistanceToNext())
                .durationToNext(activity.getDurationToNext())
                .distanceValueToNext(activity.getDistanceValueToNext())
                .durationValueToNext(activity.getDurationValueToNext())
                .rating(rating)
                .photoUrl(photoUrl)
                .notes(activity.getNotes())
                .description(activity.getDescription())
                .status(activity.getStatus().toString())
                .checkedIn(false)
                .checkedInCount(checkedInCount)
                .isAccommodation(activity.getIsAccommodation())
                .isStartingPoint(activity.getIsStartingPoint())
                .startingPointDate(activity.getStartingPointDate())
                .actualSpent(actualSpent)
                .expenseCount(expenses.size())
                .expenses(expenseResponses)
                .bookingId(activity.getBookingId())
                .bookingSource(activity.getBookingSource())
                .build();
    }

    private Place findLinkedPlace(Activity activity) {
        UUID internalId = linkedInternalPlaceId(activity);
        if (internalId != null) {
            Place place = placeRepository.findById(internalId).orElse(null);
            if (place != null) {
                return place;
            }
        }
        return isNonBlank(activity.getPlaceId()) && parseUuid(activity.getPlaceId()) == null
                ? placeRepository.findByPlaceId(activity.getPlaceId())
                : null;
    }

    private Place resolveLinkedPlace(
            Activity activity,
            Map<UUID, Place> placesById,
            Map<String, Place> placesByExternalId) {
        UUID internalId = linkedInternalPlaceId(activity);
        Place place = internalId != null ? placesById.get(internalId) : null;
        if (place != null) {
            return place;
        }
        return isNonBlank(activity.getPlaceId())
                ? placesByExternalId.get(activity.getPlaceId())
                : null;
    }

    private UUID linkedInternalPlaceId(Activity activity) {
        return activity.getPlaceRefId() != null
                ? activity.getPlaceRefId()
                : parseUuid(activity.getPlaceId());
    }

    private UUID parseUuid(String value) {
        if (!isNonBlank(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ActivityPlaceSummaryResponse mapPlaceSummary(Place place) {
        if (place == null) {
            return null;
        }
        return ActivityPlaceSummaryResponse.builder()
                .id(place.getId())
                .placeId(place.getPlaceId())
                .name(place.getTitle())
                .address(place.getAddress())
                .lat(place.getLatitude())
                .lng(place.getLongitude())
                .category(place.getCategory())
                .placeGroup(place.getPlaceGroup() != null ? place.getPlaceGroup().name() : null)
                .rating(placeRating(place, null))
                .reviewCount(place.getReviewCount())
                .thumbnail(place.getThumbnail())
                .build();
    }

    private BigDecimal placeRating(Place place, BigDecimal fallback) {
        if (place == null) {
            return fallback;
        }
        if (place.getAdjustedRating() != null) {
            return place.getAdjustedRating();
        }
        return place.getReviewRating() != null ? place.getReviewRating() : fallback;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return isNonBlank(preferred) ? preferred.trim() : fallback;
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private ExpenseResponse mapToExpenseResponse(Expense expense, List<MediaAsset> photoAssets) {
        // Handle paidBy - can be registered user or guest
        UserResponse paidByUser = null;
        if (expense.getPaidBy() != null) {
            User user = userRepository.findById(expense.getPaidBy()).orElse(null);
            if (user != null) {
                paidByUser = UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .build();
            }
        } else if (expense.getPaidByGuestName() != null) {
            // Guest payer
            paidByUser = UserResponse.builder()
                    .id(null)
                    .email(null)
                    .username(expense.getPaidByGuestName())
                    .fullName(expense.getPaidByGuestName())
                    .avatarUrl(null)
                    .build();
        }

        // Get splits
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expense.getId());
        List<ExpenseSplitResponse> splitResponses = splits.stream()
                .map(split -> {
                    // Handle guest members (userId can be null)
                    UserResponse userResponse = null;
                    if (split.getUserId() != null) {
                        User user = userRepository.findById(split.getUserId()).orElse(null);
                        if (user != null) {
                            userResponse = UserResponse.builder()
                                    .id(user.getId())
                                    .email(user.getEmail())
                                    .username(user.getUsername())
                                    .fullName(user.getFullName())
                                    .avatarUrl(user.getAvatarUrl())
                                    .build();
                        }
                    } else if (split.getGuestName() != null) {
                        // Guest member - create UserResponse with guest info
                        userResponse = UserResponse.builder()
                                .id(null) // Guest has no userId
                                .email(null)
                                .username(split.getGuestName())
                                .fullName(split.getGuestName())
                                .avatarUrl(null)
                                .build();
                    }
                    return ExpenseSplitResponse.builder()
                            .id(split.getId())
                            .user(userResponse)
                            .amount(split.getAmount())
                            .isPaid(split.getIsSettled())
                            .build();
                })
                .collect(Collectors.toList());

        List<MemoryImageResponse> photoResponses = MediaAssetResponseMapper.toImageResponses(photoAssets);
        List<String> photoUrlsList = MediaAssetResponseMapper.toUrls(photoResponses);

        return ExpenseResponse.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .category(expense.getCategory() != null ? expense.getCategory().toString() : null)
                .description(expense.getDescription())
                .activityId(expense.getActivityId())
                .paidBy(paidByUser)
                .paidByGuestMemberId(expense.getPaidByGuestMemberId())
                .splits(splitResponses)
                .photoUrls(photoUrlsList)
                .photoUrlsV2(photoResponses)
                .createdAt(expense.getCreatedAt())
                .build();
    }
}

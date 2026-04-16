package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.request.ReorderActivitiesRequest;
import com.ds.goroute.dto.request.UpdateActivityRequest;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.dto.response.ExpenseSplitResponse;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.redis.RedisService;
import com.ds.goroute.type.ActivityStatus;
import com.ds.goroute.type.TransportMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityServiceImpl implements ActivityService {
    
    private final ActivityRepository activityRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final CheckinRepository checkinRepository;
    private final RedisService redisService;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ExpenseSplitRepository expenseSplitRepository;

    @Override
    @Transactional
    public ActivityResponse createActivity(UUID tripId, CreateActivityRequest request, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        // Check if user has access
        if (!trip.getOwnerId().equals(userId) && tripMemberRepository.findByTripIdAndUserId(tripId, userId).isEmpty()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

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
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .estimatedCost(request.getEstimatedCost())
                .costCurrency(request.getCostCurrency())
                .category(request.getCategory())
                .transportMode(request.getTransportMode() != null ? TransportMode.valueOf(request.getTransportMode()) : null)
                .status(ActivityStatus.CONFIRMED)
                .isAccommodation(request.getIsAccommodation() != null ? request.getIsAccommodation() : false)
                .isStartingPoint(request.getIsStartingPoint() != null ? request.getIsStartingPoint() : false)
                .startingPointDate(request.getStartingPointDate())
                .addedBy(userId)
                .build();

        activityRepository.insert(activity);
        log.info("Activity created: {} in trip: {}", activity.getId(), tripId);
        return mapToActivityResponse(activity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityResponse> getActivities(UUID tripId, Integer dayNumber) {
        List<Activity> activities;
        if (dayNumber != null) {
            activities = activityRepository.findByTripIdAndDayNumber(tripId, dayNumber);
        } else {
            activities = activityRepository.findByTripId(tripId);
        }

        return activities.stream()
                .map(this::mapToActivityResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ActivityResponse updateActivity(UUID tripId, UUID activityId, UpdateActivityRequest request, UUID userId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        
        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        
        if (!trip.getOwnerId().equals(userId) && tripMemberRepository.findByTripIdAndUserId(tripId, userId).isEmpty()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        if (request.getPlaceId() != null) activity.setPlaceId(request.getPlaceId());
        if (request.getCustomPlaceId() != null) activity.setCustomPlaceId(request.getCustomPlaceId());
        if (request.getName() != null) activity.setName(request.getName());
        if (request.getAddress() != null) activity.setAddress(request.getAddress());
        if (request.getLat() != null) activity.setLat(request.getLat());
        if (request.getLng() != null) activity.setLng(request.getLng());
        if (request.getDayNumber() != null) activity.setDayNumber(request.getDayNumber());
        if (request.getStartTime() != null) activity.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) activity.setEndTime(request.getEndTime());
        if (request.getEstimatedCost() != null) activity.setEstimatedCost(request.getEstimatedCost());
        if (request.getCostCurrency() != null) activity.setCostCurrency(request.getCostCurrency());
        if (request.getCategory() != null) activity.setCategory(request.getCategory());
        if (request.getTransportMode() != null) activity.setTransportMode(TransportMode.valueOf(request.getTransportMode()));
        if (request.getNotes() != null) activity.setNotes(request.getNotes());
        if (request.getIsAccommodation() != null) activity.setIsAccommodation(request.getIsAccommodation());
        if (request.getIsStartingPoint() != null) activity.setIsStartingPoint(request.getIsStartingPoint());
        if (request.getStartingPointDate() != null) activity.setStartingPointDate(request.getStartingPointDate());

        activityRepository.updateById(activity);
        
        // Invalidate cache
        String cacheKey = "activities:" + tripId;
        redisService.delete(cacheKey);
        
        log.info("Activity updated: {}", activityId);
        return mapToActivityResponse(activity);
    }

    @Override
    @Transactional
    public void deleteActivity(UUID tripId, UUID activityId, UUID userId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        
        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        
        if (!trip.getOwnerId().equals(userId) && tripMemberRepository.findByTripIdAndUserId(tripId, userId).isEmpty()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        activityRepository.deleteById(activityId);
        
        // Invalidate cache
        String cacheKey = "activities:" + tripId;
        redisService.delete(cacheKey);
        
        log.info("Activity deleted: {}", activityId);
    }

    @Override
    @Transactional
    public void reorderActivities(UUID tripId, ReorderActivitiesRequest request, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (!trip.getOwnerId().equals(userId) && tripMemberRepository.findByTripIdAndUserId(tripId, userId).isEmpty()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }

        // Reorder is now based on time, so this endpoint is deprecated
        // But we keep it for backward compatibility
        log.info("Activities reorder requested in trip: {} (deprecated - order by time)", tripId);
    }

    private ActivityResponse mapToActivityResponse(Activity activity) {
        int checkedInCount = checkinRepository.findByActivityId(activity.getId()).size();
        
        // Calculate actual spent from expenses
        List<Expense> expenses = expenseRepository.findByActivityId(activity.getId());
        BigDecimal actualSpent = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Map expenses to response
        List<ExpenseResponse> expenseResponses = expenses.stream()
                .map(this::mapToExpenseResponse)
                .collect(Collectors.toList());
        
        return ActivityResponse.builder()
                .id(activity.getId())
                .tripId(activity.getTripId())
                .dayNumber(activity.getDayNumber())
                .placeId(activity.getPlaceId())
                .customPlaceId(activity.getCustomPlaceId())
                .name(activity.getName())
                .address(activity.getAddress())
                .lat(activity.getLat())
                .lng(activity.getLng())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .estimatedCost(activity.getEstimatedCost())
                .costCurrency(activity.getCostCurrency())
                .category(activity.getCategory())
                .transportMode(activity.getTransportMode() != null ? activity.getTransportMode().toString() : null)
                .rating(activity.getRating())
                .photoUrl(activity.getPhotoUrl())
                .notes(activity.getNotes())
                .status(activity.getStatus().toString())
                .checkedIn(false)
                .checkedInCount(checkedInCount)
                .isAccommodation(activity.getIsAccommodation())
                .isStartingPoint(activity.getIsStartingPoint())
                .startingPointDate(activity.getStartingPointDate())
                .actualSpent(actualSpent)
                .expenseCount(expenses.size())
                .expenses(expenseResponses)
                .build();
    }
    
    private ExpenseResponse mapToExpenseResponse(Expense expense) {
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
                    .email(expense.getPaidByGuestEmail())
                    .username(null)
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
                                .email(split.getGuestEmail())
                                .username(null)
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
        
        // Convert photoUrls array to list
        List<String> photoUrlsList = expense.getPhotoUrls() != null 
                ? Arrays.asList(expense.getPhotoUrls()) 
                : List.of();
        
        return ExpenseResponse.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .category(expense.getCategory() != null ? expense.getCategory().toString() : null)
                .description(expense.getDescription())
                .activityId(expense.getActivityId())
                .paidBy(paidByUser)
                .splits(splitResponses)
                .photoUrls(photoUrlsList)
                .createdAt(expense.getCreatedAt())
                .build();
    }
}

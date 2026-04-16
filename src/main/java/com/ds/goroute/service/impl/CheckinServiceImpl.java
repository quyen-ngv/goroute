package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CheckinRequest;
import com.ds.goroute.dto.response.CheckinResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Checkin;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.CheckinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckinServiceImpl implements CheckinService {
    
    private final CheckinRepository checkinRepository;
    private final ActivityRepository activityRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CheckinResponse checkin(UUID tripId, UUID activityId, CheckinRequest request, UUID userId) {
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

        // Check if already checked in
        var existingCheckin = checkinRepository.findByActivityIdAndUserId(activityId, userId);
        if (existingCheckin.isPresent()) {
            Checkin checkin = existingCheckin.get();
            checkin.setRating(request.getRating());
            checkin.setNotes(request.getNotes());
            checkinRepository.updateById(checkin);
            log.info("Checkin updated: {} - {}", activityId, userId);
            return mapToCheckinResponse(checkin);
        }

        Checkin checkin = Checkin.builder()
                .id(UUID.randomUUID())
                .activityId(activityId)
                .userId(userId)
                .rating(request.getRating())
                .notes(request.getNotes())
                .lat(request.getLat())
                .lng(request.getLng())
                .autoCheckin(false)
                .build();

        checkinRepository.insert(checkin);
        log.info("Checkin created: {} - {}", activityId, userId);
        return mapToCheckinResponse(checkin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckinResponse> getCheckins(UUID tripId, UUID activityId) {
        List<Checkin> checkins;
        if (activityId != null) {
            checkins = checkinRepository.findByActivityId(activityId);
        } else {
            checkins = checkinRepository.findByUserId(tripId); // TODO: Fix this - should be by trip
        }

        return checkins.stream()
                .map(this::mapToCheckinResponse)
                .collect(Collectors.toList());
    }

    private CheckinResponse mapToCheckinResponse(Checkin checkin) {
        User user = userRepository.findById(checkin.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));
        Activity activity = activityRepository.findById(checkin.getActivityId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        
        return CheckinResponse.builder()
                .id(checkin.getId())
                .activityId(checkin.getActivityId())
                .user(mapToUserResponse(user))
                .checkedInAt(checkin.getCheckedInAt())
                .rating(checkin.getRating())
                .notes(checkin.getNotes())
                .lat(checkin.getLat())
                .lng(checkin.getLng())
                .build();
    }

    private com.ds.goroute.dto.response.UserResponse mapToUserResponse(User user) {
        if (user == null) return null;
        return com.ds.goroute.dto.response.UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .defaultCurrency(user.getDefaultCurrency())
                .language(user.getLanguage())
                .build();
    }
}

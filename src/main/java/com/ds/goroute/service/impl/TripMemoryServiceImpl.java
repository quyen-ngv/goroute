package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateTripMemoryRequest;
import com.ds.goroute.dto.response.TripMemoryResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.TripMemoryService;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripMemoryServiceImpl implements TripMemoryService {
    private static final int FREE_TRIP_MEMORY_LIMIT = 20;

    private final MediaAssetRepository mediaAssetRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final AiTripRepository aiTripRepository;
    private final ImageStorageCleanupService imageStorageCleanupService;

    @Override
    @Transactional(readOnly = true)
    public List<TripMemoryResponse> getTripMemories(UUID tripId, UUID userId, UUID activityId) {
        Trip trip = getTripAndEnsureMember(tripId, userId);
        List<MediaAsset> assets = activityId != null
                ? mediaAssetRepository.findByActivityId(activityId)
                : mediaAssetRepository.findByTripId(tripId);

        return assets.stream()
                .filter(asset -> trip.getId().equals(asset.getTripId()))
                .map(asset -> toResponse(asset, userRepository.findById(asset.getUploadedBy()).orElse(null)))
                .toList();
    }

    @Override
    @Transactional
    public TripMemoryResponse addTripMemory(UUID tripId, CreateTripMemoryRequest request, UUID userId) {
        Trip trip = getTripAndEnsureMember(tripId, userId);
        validateActivity(tripId, request.getActivityId());

        if (!isProTripOwner(trip.getOwnerId()) && mediaAssetRepository.countByTripId(tripId) >= FREE_TRIP_MEMORY_LIMIT) {
            throw new BusinessException(ErrorConstant.FREE_TRIP_MEMORY_LIMIT_REACHED);
        }

        MediaAsset mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .activityId(request.getActivityId())
                .entityType(request.getActivityId() != null ? "TRIP_ACTIVITY_MEMORY" : "TRIP_MEMORY")
                .entityId(request.getActivityId() != null ? request.getActivityId() : tripId)
                .url(request.getUrl().trim())
                .caption(request.getCaption())
                .uploadedBy(userId)
                .build();

        mediaAssetRepository.insert(mediaAsset);
        return toResponse(mediaAsset, userRepository.findById(userId).orElse(null));
    }

    @Override
    @Transactional
    public void deleteTripMemory(UUID tripId, UUID memoryId, UUID userId) {
        getTripAndEnsureMember(tripId, userId);
        MediaAsset asset = mediaAssetRepository.findById(memoryId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Memory not found"));

        if (!tripId.equals(asset.getTripId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Memory not found");
        }

        imageStorageCleanupService.deleteImagesForEntityRecord("MEDIA_ASSET", memoryId);
        mediaAssetRepository.softDelete(memoryId);
    }

    private Trip getTripAndEnsureMember(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        if (trip.getOwnerId().equals(userId)) {
            return trip;
        }

        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId).orElse(null);
        if (member == null || member.getStatus() != MemberStatus.ACCEPTED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You are not a trip member");
        }

        return trip;
    }

    private void validateActivity(UUID tripId, UUID activityId) {
        if (activityId == null) return;
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        if (!tripId.equals(activity.getTripId())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Activity does not belong to this trip");
        }
    }

    private boolean isProTripOwner(UUID ownerId) {
        aiTripRepository.ensureSubscription(ownerId);
        return "PRO".equalsIgnoreCase(aiTripRepository.getSubscriptionTier(ownerId));
    }

    private TripMemoryResponse toResponse(MediaAsset asset, User user) {
        return TripMemoryResponse.builder()
                .id(asset.getId())
                .tripId(asset.getTripId())
                .activityId(asset.getActivityId())
                .url(asset.getUrl())
                .caption(asset.getCaption())
                .uploadedBy(asset.getUploadedBy())
                .uploaderName(user != null ? user.getFullName() : null)
                .uploaderAvatarUrl(user != null ? user.getAvatarUrl() : null)
                .createdAt(asset.getCreatedAt())
                .build();
    }
}

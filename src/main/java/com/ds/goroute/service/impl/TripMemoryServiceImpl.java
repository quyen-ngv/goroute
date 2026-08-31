package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateTripMemoryRequest;
import com.ds.goroute.dto.request.UpdateTripMemoryRequest;
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
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.TripMemoryService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.utils.MemoryImageUrlNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripMemoryServiceImpl implements TripMemoryService {
    private final MediaAssetRepository mediaAssetRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final AiTripRepository aiTripRepository;
    private final BusinessConfigService businessConfigService;
    private final FileUploadService fileUploadService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final NotificationHelper notificationHelper;

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

        ensureMemoryCapacity(tripId, trip);
        String url = MemoryImageUrlNormalizer.normalize(request.getUrl())
                .orElseThrow(() -> new BusinessException(
                        ErrorConstant.INVALID_PARAMETERS, "Memory URL must be a valid HTTP URL"));

        MediaAsset mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .activityId(request.getActivityId())
                .entityType(request.getActivityId() != null ? "TRIP_ACTIVITY_MEMORY" : "TRIP_MEMORY")
                .entityId(request.getActivityId() != null ? request.getActivityId() : tripId)
                .mediaType("IMAGE")
                .url(url)
                .caption(request.getCaption())
                .description(request.getDescription())
                .takenAt(request.getTakenAt())
                .dateSource(request.getTakenAt() == null ? "UPLOAD" : request.getDateSource())
                .captureSource(request.getCaptureSource())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .placeId(request.getPlaceId())
                .locationName(trimToNull(request.getLocationName()))
                .locationSource(trimToNull(request.getLocationSource()))
                .uploadedBy(userId)
                .build();

        mediaAssetRepository.insert(mediaAsset);
        notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.MEMORY_ADDED,
                memoryNotificationData(trip, mediaAsset, userId), null);
        return toResponse(mediaAsset, userRepository.findById(userId).orElse(null));
    }

    @Override
    @Transactional
    public TripMemoryResponse addTripVideoMemory(
            UUID tripId,
            UUID activityId,
            MultipartFile file,
            UUID userId) {
        Trip trip = getTripAndEnsureMember(tripId, userId);
        validateActivity(tripId, activityId);
        if (!isProUser(userId)) {
            throw new BusinessException(ErrorConstant.PRO_VIDEO_UPLOAD_REQUIRED);
        }
        ensureMemoryCapacity(tripId, trip);

        String url = fileUploadService.uploadVideo(userId, file);
        MediaAsset mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .activityId(activityId)
                .entityType(activityId != null ? "TRIP_ACTIVITY_MEMORY" : "TRIP_MEMORY")
                .entityId(activityId != null ? activityId : tripId)
                .mediaType("VIDEO")
                .url(url)
                .uploadedBy(userId)
                .build();

        mediaAssetRepository.insert(mediaAsset);
        notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.MEMORY_ADDED,
                memoryNotificationData(trip, mediaAsset, userId), null);
        return toResponse(mediaAsset, userRepository.findById(userId).orElse(null));
    }

    @Override
    @Transactional
    public TripMemoryResponse updateTripMemory(
            UUID tripId,
            UUID memoryId,
            UpdateTripMemoryRequest request,
            UUID userId) {
        getTripAndEnsureMember(tripId, userId);
        MediaAsset asset = getMemoryOfTrip(tripId, memoryId);

        // Only the uploader edits the words on their own memory. Trip membership
        // is what lets you see it; it is not what lets you rewrite someone
        // else's caption.
        if (!userId.equals(asset.getUploadedBy())) {
            throw new BusinessException(
                    ErrorConstant.FORBIDDEN_ERROR, "You can only edit your own memory");
        }

        asset.setCaption(trimToNull(request.getCaption()));
        asset.setDescription(trimToNull(request.getDescription()));

        // A date the author typed is worth keeping, but it stops being evidence:
        // marking it MANUAL is what lets a reader tell it apart from EXIF.
        if (request.getTakenAt() != null
                && !request.getTakenAt().equals(asset.getTakenAt())) {
            asset.setTakenAt(request.getTakenAt());
            asset.setDateSource("MANUAL");
        }

        mediaAssetRepository.updateDetails(asset);

        return toResponse(asset, userRepository.findById(asset.getUploadedBy()).orElse(null));
    }

    @Override
    @Transactional
    public void deleteTripMemory(UUID tripId, UUID memoryId, UUID userId) {
        getTripAndEnsureMember(tripId, userId);
        MediaAsset asset = getMemoryOfTrip(tripId, memoryId);

        imageStorageCleanupService.deleteImagesForEntityRecord("MEDIA_ASSET", memoryId);
        mediaAssetRepository.softDelete(memoryId);
        notificationHelper.emitGenericToMembers(tripId, userId, NotificationType.MEMORY_DELETED,
                memoryNotificationData(
                        tripRepository.findById(tripId).orElseThrow(), asset, userId), null);
    }

    /**
     * A memory id that belongs to another trip is reported as not found rather
     * than forbidden: answering "wrong trip" would confirm the id exists.
     */
    private MediaAsset getMemoryOfTrip(UUID tripId, UUID memoryId) {
        MediaAsset asset = mediaAssetRepository.findById(memoryId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Memory not found"));
        if (!tripId.equals(asset.getTripId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Memory not found");
        }
        return asset;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        return isProUser(ownerId);
    }

    private boolean isProUser(UUID userId) {
        aiTripRepository.ensureSubscription(userId);
        return "PRO".equalsIgnoreCase(aiTripRepository.getSubscriptionTier(userId));
    }

    private void ensureMemoryCapacity(UUID tripId, Trip trip) {
        int freeTripMemoryLimit = businessConfigService.getInt(BusinessConfigKey.FREE_TRIP_MEMORY_LIMIT);
        if (!isProTripOwner(trip.getOwnerId()) && mediaAssetRepository.countByTripId(tripId) >= freeTripMemoryLimit) {
            throw new BusinessException(ErrorConstant.FREE_TRIP_MEMORY_LIMIT_REACHED);
        }
    }

    private Map<String, Object> memoryNotificationData(Trip trip, MediaAsset asset, UUID actorId) {
        Map<String, Object> data = new HashMap<>();
        data.put("actorName", notificationHelper.actorName(actorId));
        data.put("tripName", trip.getName());
        data.put("memoryId", asset.getId());
        if (asset.getCaption() != null && !asset.getCaption().isBlank()) {
            data.put("memoryName", asset.getCaption());
        }
        if (asset.getActivityId() != null) {
            data.put("activityId", asset.getActivityId());
            data.put("deepLink", "/trip/" + trip.getId() + "/activities/" + asset.getActivityId());
        } else {
            data.put("deepLink", "/trip/" + trip.getId());
        }
        return data;
    }

    private TripMemoryResponse toResponse(MediaAsset asset, User user) {
        return TripMemoryResponse.builder()
                .id(asset.getId())
                .tripId(asset.getTripId())
                .activityId(asset.getActivityId())
                .mediaType(asset.getMediaType() == null ? "IMAGE" : asset.getMediaType())
                .url(MemoryImageUrlNormalizer.normalize(asset.getUrl()).orElse(asset.getUrl()))
                .caption(asset.getCaption())
                .description(asset.getDescription())
                .takenAt(asset.getTakenAt())
                .dateSource(asset.getDateSource())
                .captureSource(asset.getCaptureSource())
                .latitude(asset.getLatitude())
                .longitude(asset.getLongitude())
                .placeId(asset.getPlaceId())
                .locationName(asset.getLocationName())
                .locationSource(asset.getLocationSource())
                .uploadedBy(asset.getUploadedBy())
                .uploaderName(user != null ? user.getFullName() : null)
                .uploaderAvatarUrl(user != null ? user.getAvatarUrl() : null)
                .createdAt(asset.getCreatedAt())
                .build();
    }
}

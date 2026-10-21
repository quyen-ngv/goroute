package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.CheckinPhotoResponse;
import com.ds.goroute.dto.response.PublicProfileCheckinResponse;
import com.ds.goroute.dto.response.PublicProfileTripResponse;
import com.ds.goroute.dto.response.PublicUserProfileResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.PublicProfileService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.UserService;
import com.ds.goroute.type.ModeratedContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicProfileServiceImpl implements PublicProfileService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserCheckinService checkinService;
    private final TripRepository tripRepository;
    private final ContentModerationService contentModerationService;

    @Override
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getProfile(String username, UUID viewerId) {
        return userService.getPublicProfile(requireUser(username).getId(), viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicUserProfileResponse> findGuestProfile(String username) {
        return findUser(username).map(user -> userService.getPublicProfile(user.getId(), null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicProfileCheckinResponse> getCheckins(String username, int page, int size) {
        // Always the stranger's view, even when the owner opens their own link: the page is
        // what the people they send it to will see.
        return checkinService.byUser(null, requireUser(username).getId(), page, size).stream()
                .map(PublicProfileServiceImpl::toCheckin)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicProfileTripResponse> getTrips(String username, int page, int size) {
        List<Trip> trips = tripRepository.findPublicTripsByOwnerId(requireUser(username).getId());
        Set<UUID> takenDown = contentModerationService.takenDownIds(
                ModeratedContentType.TRIP, trips.stream().map(Trip::getId).toList());
        return trips.stream()
                .filter(trip -> !takenDown.contains(trip.getId()))
                .skip((long) page * size)
                .limit(size)
                .map(PublicProfileServiceImpl::toTrip)
                .toList();
    }

    private User requireUser(String username) {
        return findUser(username)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));
    }

    /** Usernames are stored lower-case; a link typed with capitals or a leading @ still opens. */
    private Optional<User> findUser(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized.isEmpty() ? Optional.empty() : userRepository.findByUsername(normalized);
    }

    private static PublicProfileCheckinResponse toCheckin(UserCheckinResponse checkin) {
        List<CheckinPhotoResponse> photos = checkin.getPhotos() == null ? List.of() : checkin.getPhotos();
        return PublicProfileCheckinResponse.builder()
                .id(checkin.getId())
                .placeId(checkin.getPlaceId())
                .placeName(firstPresent(checkin.getPlaceName(), checkin.getCustomName(), checkin.getLocationName()))
                .province(checkin.getProvince())
                .caption(checkin.getCaption())
                .overallRating(checkin.getOverallRating())
                .photoUrls(photos.stream().map(CheckinPhotoResponse::getUrl).filter(Objects::nonNull).toList())
                .likeCount(checkin.getLikeCount())
                .commentCount(checkin.getCommentCount())
                .visitedAt(checkin.getVisitedAt())
                .createdAt(checkin.getCreatedAt())
                .build();
    }

    private static PublicProfileTripResponse toTrip(Trip trip) {
        return PublicProfileTripResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .destination(trip.getDestination())
                .coverImageUrl(trip.getCoverImageUrl())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .publicSharedAt(trip.getPublicSharedAt())
                .build();
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

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
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.UserService;
import com.ds.goroute.type.ModeratedContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicProfileServiceImplTest {

    private final UUID userId = UUID.randomUUID();
    private UserRepository userRepository;
    private UserService userService;
    private UserCheckinService checkinService;
    private TripRepository tripRepository;
    private ContentModerationService moderation;
    private PublicProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = mock(UserService.class);
        checkinService = mock(UserCheckinService.class);
        tripRepository = mock(TripRepository.class);
        moderation = mock(ContentModerationService.class);
        service = new PublicProfileServiceImpl(userRepository, userService, checkinService, tripRepository, moderation);
        when(userRepository.findByUsername(any())).thenAnswer(invocation ->
                "an.food".equals(invocation.getArgument(0))
                        ? Optional.of(User.builder().id(userId).username("an.food").build())
                        : Optional.empty());
    }

    @Test
    void opensALinkTypedWithCapitalsOrALeadingAt() {
        PublicUserProfileResponse profile = PublicUserProfileResponse.builder().id(userId).username("an.food").build();
        UUID viewerId = UUID.randomUUID();
        when(userService.getPublicProfile(userId, viewerId)).thenReturn(profile);

        assertThat(service.getProfile(" @An.Food ", viewerId)).isSameAs(profile);
    }

    @Test
    void anUnknownUsernameIsNotFound() {
        assertThatThrownBy(() -> service.getProfile("nobody", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getError().getCode()).isEqualTo(ErrorConstant.NOT_FOUND));
        assertThatThrownBy(() -> service.getCheckins("  ", 0, 12)).isInstanceOf(BusinessException.class);
        assertThat(service.findGuestProfile("nobody")).isEmpty();
        verify(userService, never()).getPublicProfile(any(), any());
    }

    @Test
    void theGuestProfileHasNoViewer() {
        PublicUserProfileResponse profile = PublicUserProfileResponse.builder().id(userId).build();
        when(userService.getPublicProfile(userId, null)).thenReturn(profile);

        assertThat(service.findGuestProfile("an.food")).containsSame(profile);
    }

    @Test
    void checkinsAreAlwaysTheStrangersViewAndCarryOnlyWhatTheCardShows() {
        UserCheckinResponse checkin = UserCheckinResponse.builder()
                .id(UUID.randomUUID())
                .placeName(null)
                .customName("Bún chả cô Hà")
                .locationName("12 Hàng Mành")
                .latitude(new BigDecimal("21.03"))
                .longitude(new BigDecimal("105.85"))
                .province("Hà Nội")
                .caption("Ngon")
                .overallRating(5)
                .rewardPoints(30)
                .photos(List.of(
                        CheckinPhotoResponse.builder().url("https://cdn/a.jpg").build(),
                        CheckinPhotoResponse.builder().url(null).build()))
                .likeCount(4)
                .commentCount(2)
                .build();
        when(checkinService.byUser(null, userId, 1, 12)).thenReturn(List.of(checkin));

        List<PublicProfileCheckinResponse> page = service.getCheckins("an.food", 1, 12);

        verify(checkinService).byUser(null, userId, 1, 12);
        assertThat(page).singleElement().satisfies(card -> {
            assertThat(card.getPlaceName()).isEqualTo("Bún chả cô Hà");
            assertThat(card.getPhotoUrls()).containsExactly("https://cdn/a.jpg");
            assertThat(card.getOverallRating()).isEqualTo(5);
            assertThat(card.getLikeCount()).isEqualTo(4);
            assertThat(card.getCommentCount()).isEqualTo(2);
        });
    }

    @Test
    void tripsSkipTakenDownOnesBeforePaging() {
        Trip first = trip("Đà Lạt");
        Trip removed = trip("Gỡ");
        Trip second = trip("Huế");
        Trip third = trip("Hội An");
        when(tripRepository.findPublicTripsByOwnerId(userId)).thenReturn(List.of(first, removed, second, third));
        when(moderation.takenDownIds(eq(ModeratedContentType.TRIP), anyCollection())).thenReturn(Set.of(removed.getId()));

        List<PublicProfileTripResponse> pageZero = service.getTrips("an.food", 0, 2);
        List<PublicProfileTripResponse> pageOne = service.getTrips("an.food", 1, 2);

        assertThat(pageZero).extracting(PublicProfileTripResponse::getName).containsExactly("Đà Lạt", "Huế");
        assertThat(pageOne).extracting(PublicProfileTripResponse::getName).containsExactly("Hội An");
    }

    private static Trip trip(String name) {
        return Trip.builder().id(UUID.randomUUID()).name(name).destination(name).build();
    }
}

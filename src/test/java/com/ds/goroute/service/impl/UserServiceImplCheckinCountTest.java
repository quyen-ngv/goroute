package com.ds.goroute.service.impl;

import com.ds.goroute.entity.User;
import com.ds.goroute.mapper.PlaceContributionMapper;
import com.ds.goroute.repository.RefreshTokenRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.TrackService;
import com.ds.goroute.service.UserDeviceService;
import com.ds.goroute.service.UserGuideService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The profile's check-in count is the size of the list GET /checkins/users/{userId} pages
 * through for the same viewer, not the length of whichever page the app happened to load.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplCheckinCountTest {

    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private UserGuideService guides;
    @Mock private UserReviewRepository reviews;
    @Mock private TripRepository trips;
    @Mock private PlaceContributionMapper contributions;
    @Mock private StorageService storage;
    @Mock private FileUploadService uploads;
    @Mock private ImageStorageCleanupService imageCleanup;
    @Mock private UserDeviceService devices;
    @Mock private TrackService tracks;
    @Mock private UserCheckinRepository checkins;

    @InjectMocks private UserServiceImpl service;

    private final UUID owner = UUID.randomUUID();

    @Test
    void ownProfileCountsPrivateCheckinsToo() {
        when(users.findById(owner)).thenReturn(Optional.of(User.builder().id(owner).build()));
        when(checkins.countByUser(owner, true)).thenReturn(57L);

        assertThat(service.getProfile(owner).getCheckinCount()).isEqualTo(57L);
    }

    @Test
    void someoneElsesProfileCountsOnlyWhatTheyCanList() {
        UUID viewer = UUID.randomUUID();
        when(users.findById(owner)).thenReturn(Optional.of(User.builder().id(owner).build()));
        when(checkins.countByUser(owner, false)).thenReturn(21L);

        assertThat(service.getPublicProfile(owner, viewer).getCheckinCount()).isEqualTo(21L);
    }

    @Test
    void viewingYourOwnPublicProfileIsTheOwnerCount() {
        when(users.findById(owner)).thenReturn(Optional.of(User.builder().id(owner).build()));
        when(checkins.countByUser(owner, true)).thenReturn(57L);

        assertThat(service.getPublicProfile(owner, owner).getCheckinCount()).isEqualTo(57L);
    }
}

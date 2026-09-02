package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateTripMemoryRequest;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
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
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TripMemoryServiceImplTest {

    private final MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AiTripRepository aiTripRepository = mock(AiTripRepository.class);
    private final BusinessConfigService businessConfigService = mock(BusinessConfigService.class);
    private final FileUploadService fileUploadService = mock(FileUploadService.class);
    private final ImageStorageCleanupService imageStorageCleanupService = mock(ImageStorageCleanupService.class);
    private final NotificationHelper notificationHelper = mock(NotificationHelper.class);

    /**
     * A real guard over the same mocked repositories rather than a mock of the guard itself.
     *
     * <p>The membership rules moved out of this service and into the guard; mocking the guard
     * would move them out of the test too, and these cases exist precisely to prove that an
     * uploader who is not an editor is refused before any file is written.
     */
    private final TripAccessGuard tripAccessGuard =
            new TripAccessGuard(tripRepository, tripMemberRepository);

    private final TripMemoryServiceImpl service = new TripMemoryServiceImpl(
            mediaAssetRepository,
            tripAccessGuard,
            tripRepository,
            tripMemberRepository,
            activityRepository,
            userRepository,
            aiTripRepository,
            businessConfigService,
            fileUploadService,
            imageStorageCleanupService,
            notificationHelper);

    @Test
    void usesConfiguredLimitForFreeTripMemory() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).ownerId(ownerId).name("Test trip").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(businessConfigService.getInt(BusinessConfigKey.FREE_TRIP_MEMORY_LIMIT)).thenReturn(75);
        when(aiTripRepository.getSubscriptionTier(ownerId)).thenReturn("FREE");
        when(mediaAssetRepository.countByTripId(tripId)).thenReturn(50);
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThatCode(() -> service.addTripMemory(tripId, request(), ownerId))
                .doesNotThrowAnyException();

        verify(mediaAssetRepository).insert(any());
    }

    @Test
    void rejectsFreeTripMemoryAtConfiguredLimit() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).ownerId(ownerId).name("Test trip").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(businessConfigService.getInt(BusinessConfigKey.FREE_TRIP_MEMORY_LIMIT)).thenReturn(50);
        when(aiTripRepository.getSubscriptionTier(ownerId)).thenReturn("FREE");
        when(mediaAssetRepository.countByTripId(tripId)).thenReturn(50);

        assertThatThrownBy(() -> service.addTripMemory(tripId, request(), ownerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.FREE_TRIP_MEMORY_LIMIT_REACHED));

        verify(mediaAssetRepository, never()).insert(any());
    }

    @Test
    void rejectsVideoUploadForNonProUploaderBeforeWritingFile() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).ownerId(ownerId).name("Test trip").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, uploaderId)).thenReturn(
                Optional.of(TripMember.builder().tripId(tripId).userId(uploaderId).status(MemberStatus.ACCEPTED).build()));
        when(aiTripRepository.getSubscriptionTier(uploaderId)).thenReturn("FREE");

        assertThatThrownBy(() -> service.addTripVideoMemory(
                        tripId,
                        null,
                        new MockMultipartFile("file", "memory.mp4", "video/mp4", validMp4Header()),
                        uploaderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.PRO_VIDEO_UPLOAD_REQUIRED));

        verifyNoInteractions(fileUploadService);
        verify(mediaAssetRepository, never()).insert(any());
    }

    private byte[] validMp4Header() {
        return new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    }

    private CreateTripMemoryRequest request() {
        CreateTripMemoryRequest request = new CreateTripMemoryRequest();
        request.setUrl("https://example.com/memory.jpg");
        return request;
    }
}

package com.ds.goroute.service;

import com.ds.goroute.repository.UserCheckinRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemovedCheckinPhotoCleanupServiceTest {

    private final UserCheckinRepository checkins = mock(UserCheckinRepository.class);
    private final ImageStorageCleanupService images = mock(ImageStorageCleanupService.class);
    private final RemovedCheckinPhotoCleanupService service =
            new RemovedCheckinPhotoCleanupService(checkins, images);

    @Test
    void removesLegacyPhotoRowsOnlyAfterSchedulingTheirManagedObjectCleanup() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(checkins.findRemovedCheckinIdsWithPhotos(100)).thenReturn(List.of(first, second));

        int purged = service.purgeRemovedCheckinPhotos(100);

        assertThat(purged).isEqualTo(2);
        InOrder ordered = inOrder(images, checkins);
        ordered.verify(images).deleteImagesForEntityRecord("USER_CHECKIN_PHOTO", first);
        ordered.verify(checkins).deletePhotos(first);
        ordered.verify(images).deleteImagesForEntityRecord("USER_CHECKIN_PHOTO", second);
        ordered.verify(checkins).deletePhotos(second);
    }

    @Test
    void makesAnInvalidBatchSizeSafe() {
        when(checkins.findRemovedCheckinIdsWithPhotos(1)).thenReturn(List.of());

        assertThat(service.purgeRemovedCheckinPhotos(0)).isZero();
    }
}

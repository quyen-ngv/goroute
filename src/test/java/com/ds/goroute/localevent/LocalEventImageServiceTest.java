package com.ds.goroute.localevent;

import com.ds.goroute.localevent.domain.LocalEventImage;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.localevent.service.LocalEventImageService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.type.ModerationCategory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalEventImageServiceTest {

    private final LocalEventRepository repository = mock(LocalEventRepository.class);
    private final FileUploadService uploads = mock(FileUploadService.class);
    private final LocalEventImageService service = new LocalEventImageService(repository, uploads);

    @Test
    void theCoverImageIsReHostedThroughTheGuardedPathAndBecomesTheCover() {
        LocalEventImage cover = image(0, 0);
        when(repository.findPendingImages(3, 20)).thenReturn(List.of(cover));
        when(uploads.uploadImageFromPublicUrl(any(), eq(cover.getSourceUrl())))
                .thenReturn(ImageUploadOutcome.accepted(cover.getSourceUrl(), "https://cdn/events/x.webp"));
        when(repository.markImageDone(eq(cover.getId()), eq("https://cdn/events/x.webp"), any())).thenReturn(true);

        assertThat(service.processPending()).isEqualTo(1);

        ArgumentCaptor<ImageUploadRequest> request = ArgumentCaptor.forClass(ImageUploadRequest.class);
        verify(uploads).uploadImageFromPublicUrl(request.capture(), eq(cover.getSourceUrl()));
        assertThat(request.getValue().entryPoint()).isEqualTo(ImageUploadRequest.ImageEntryPoint.LOCAL_EVENT);
        assertThat(request.getValue().objectPrefix()).isEqualTo("events/" + cover.getEventId());
        verify(repository).updateCoverFromImage(cover.getEventId(), cover.getId());
    }

    @Test
    void aFailedDownloadIsRetriedUntilTheThirdAttempt() {
        LocalEventImage first = image(1, 0);
        LocalEventImage last = image(1, 2);
        when(repository.findPendingImages(3, 20)).thenReturn(List.of(first, last));
        when(uploads.uploadImageFromPublicUrl(any(), anyString()))
                .thenReturn(ImageUploadOutcome.failed("x", "Remote file URL must resolve to a public address"));

        assertThat(service.processPending()).isZero();

        verify(repository).markImageAttemptFailed(eq(first.getId()), eq("PENDING"), anyString(), any());
        verify(repository).markImageAttemptFailed(eq(last.getId()), eq("FAILED"),
                eq("Remote file URL must resolve to a public address"), any());
        verify(repository, never()).updateCoverFromImage(any(), any());
    }

    @Test
    void aModerationRejectionFailsAtOnce() {
        LocalEventImage image = image(0, 0);
        when(repository.findPendingImages(3, 20)).thenReturn(List.of(image));
        when(uploads.uploadImageFromPublicUrl(any(), anyString()))
                .thenReturn(ImageUploadOutcome.rejectedByModeration("x", ModerationCategory.values()[0], "Rejected"));

        service.processPending();

        verify(repository).markImageAttemptFailed(eq(image.getId()), eq("FAILED"), eq("Rejected"), any());
    }

    private static LocalEventImage image(int position, int attempts) {
        return LocalEventImage.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .position(position)
                .sourceUrl("https://images.example.com/" + UUID.randomUUID() + ".jpg")
                .status("PENDING")
                .attempts(attempts)
                .build();
    }
}

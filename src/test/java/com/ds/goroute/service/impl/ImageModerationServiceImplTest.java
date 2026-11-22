package com.ds.goroute.service.impl;

import com.ds.goroute.config.ImageModerationProperties;
import com.ds.goroute.entity.ImageModerationResult;
import com.ds.goroute.repository.ModerationAuditRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.HiddenImageService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.moderation.ImageModerationProvider;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ImageModerationStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.ModerationFlagSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageModerationServiceImplTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final UUID USER_ID = UUID.randomUUID();

    private final ImageModerationProvider provider = mock(ImageModerationProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ImageModerationProvider> providers = mock(ObjectProvider.class);
    private final ModerationAuditRepository auditRepository = mock(ModerationAuditRepository.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final ContentModerationService contentModerationService = mock(ContentModerationService.class);
    private final HiddenImageService hiddenImageService = mock(HiddenImageService.class);
    private final StorageService storageService = mock(StorageService.class);
    /** Tasks handed to the pool, run by the test when it chooses. */
    private final List<Runnable> scheduled = new ArrayList<>();

    private final ImageModerationServiceImpl service = new ImageModerationServiceImpl(
            providers, auditRepository, config, new ImageModerationProperties(),
            contentModerationService, hiddenImageService, storageService, scheduled::add);

    @BeforeEach
    void setUp() {
        when(providers.getIfAvailable()).thenReturn(provider);
        when(provider.name()).thenReturn("test");
        when(config.getBoolean(BusinessConfigKey.MODERATION_IMAGE_ENABLED)).thenReturn(true);
        when(config.getDecimal(BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_SEXUAL)).thenReturn(0.85d);
        when(config.getDecimal(BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_SPAM)).thenReturn(0.95d);
        when(auditRepository.completeImageResult(any())).thenReturn(1);
    }

    @Test
    void submitRecordsAPendingRowAndReturnsBeforeTheProviderIsCalled() {
        service.submit(USER_ID, "user-upload", "expenses/u/a.jpg", "https://cdn/expenses/u/a.jpg", JPEG, "image/jpeg");

        ArgumentCaptor<ImageModerationResult> row = ArgumentCaptor.forClass(ImageModerationResult.class);
        verify(auditRepository).insertImageResult(row.capture());
        assertThat(row.getValue().getStatus()).isEqualTo(ImageModerationStatus.PENDING);
        assertThat(row.getValue().getDecision()).isNull();
        assertThat(row.getValue().getObjectKey()).isEqualTo("expenses/u/a.jpg");
        assertThat(row.getValue().getUrl()).isEqualTo("https://cdn/expenses/u/a.jpg");
        assertThat(scheduled).hasSize(1);
        verify(provider, never()).score(any(), any());
    }

    @Test
    void submitDoesNothingWhenImageModerationIsOff() {
        when(config.getBoolean(BusinessConfigKey.MODERATION_IMAGE_ENABLED)).thenReturn(false);

        service.submit(USER_ID, "user-upload", "k", "u", JPEG, "image/jpeg");

        verifyNoInteractions(auditRepository);
        assertThat(scheduled).isEmpty();
    }

    @Test
    void aSevereScoreFlagsAndHidesTheImage() {
        when(provider.score(any(), any())).thenReturn(Map.of(ModerationCategory.SEXUAL, 0.97d));

        submitAndRun();

        ArgumentCaptor<ImageModerationResult> image = ArgumentCaptor.forClass(ImageModerationResult.class);
        verify(contentModerationService).flag(eq(ModeratedContentType.IMAGE), any(), eq(USER_ID),
                eq(ModerationFlagSource.IMAGE_MODERATION),
                eq(ModerationVerdict.of(ModerationAction.BLOCK, ModerationCategory.SEXUAL, null, "SEXUAL 0.97")),
                anyString());
        verify(hiddenImageService).hide(image.capture(), eq(ModerationCategory.SEXUAL));
        assertThat(image.getValue().getObjectKey()).isEqualTo("expenses/u/a.jpg");
        ArgumentCaptor<ImageModerationResult> done = ArgumentCaptor.forClass(ImageModerationResult.class);
        verify(auditRepository).completeImageResult(done.capture());
        assertThat(done.getValue().getDecision()).isEqualTo(ModerationAction.BLOCK);
    }

    @Test
    void aNonSevereScoreQueuesTheImageButLeavesItVisible() {
        when(provider.score(any(), any())).thenReturn(Map.of(ModerationCategory.SPAM, 0.99d));

        submitAndRun();

        verify(contentModerationService).flag(eq(ModeratedContentType.IMAGE), any(), eq(USER_ID),
                eq(ModerationFlagSource.IMAGE_MODERATION), any(), anyString());
        verify(hiddenImageService, never()).hide(any(), any());
    }

    @Test
    void aCleanImageIsOnlyRecorded() {
        when(provider.score(any(), any())).thenReturn(Map.of(ModerationCategory.SEXUAL, 0.10d));

        submitAndRun();

        verify(auditRepository).completeImageResult(any());
        verifyNoInteractions(contentModerationService, hiddenImageService);
    }

    @Test
    void aProviderOutageIsRecordedAndTheImageStaysUp() {
        when(provider.score(any(), any())).thenThrow(new IllegalStateException("down"));

        submitAndRun();

        ArgumentCaptor<ImageModerationResult> done = ArgumentCaptor.forClass(ImageModerationResult.class);
        verify(auditRepository).completeImageResult(done.capture());
        assertThat(done.getValue().getDecision()).isEqualTo(ModerationAction.LOG);
        verifyNoInteractions(contentModerationService, hiddenImageService);
    }

    /** The worker and the retry job may both reach a row; only the one that completes it acts. */
    @Test
    void aRowAnotherRunAlreadyCompletedIsNotFlaggedTwice() {
        when(provider.score(any(), any())).thenReturn(Map.of(ModerationCategory.SEXUAL, 0.97d));
        when(auditRepository.completeImageResult(any())).thenReturn(0);

        submitAndRun();

        verifyNoInteractions(contentModerationService, hiddenImageService);
    }

    @Test
    void resumeReadsAStaleImageBackFromStorage() {
        ImageModerationResult stale = ImageModerationResult.builder()
                .id(UUID.randomUUID()).objectKey("expenses/u/b.jpg").mimeType("image/jpeg")
                .userId(USER_ID).entryPoint("user-upload").status(ImageModerationStatus.PENDING).build();
        when(auditRepository.findStalePendingImageResults(any(), anyInt())).thenReturn(List.of(stale));
        when(storageService.readObject("expenses/u/b.jpg")).thenReturn(JPEG);
        when(provider.score(any(), any())).thenReturn(Map.of(ModerationCategory.SEXUAL, 0.97d));

        int resumed = service.resumeStale(Duration.ofMinutes(5), 50);

        assertThat(resumed).isEqualTo(1);
        verify(provider).score(JPEG, "image/jpeg");
        verify(hiddenImageService).hide(stale, ModerationCategory.SEXUAL);
    }

    private void submitAndRun() {
        service.submit(USER_ID, "user-upload", "expenses/u/a.jpg", "https://cdn/expenses/u/a.jpg", JPEG, "image/jpeg");
        assertThat(scheduled).hasSize(1);
        scheduled.get(0).run();
    }
}

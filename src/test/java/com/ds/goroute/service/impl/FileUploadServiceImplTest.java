package com.ds.goroute.service.impl;

import com.ds.goroute.config.FileUploadProperties;
import com.ds.goroute.config.ImgpressProperties;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ImageModerationService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ds.goroute.repository.AiTripRepository;

class FileUploadServiceImplTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final StorageService storageService = mock(StorageService.class);
    private final ImageModerationService imageModerationService = mock(ImageModerationService.class);
    private final BusinessConfigService businessConfigService = mock(BusinessConfigService.class);
    private final AiTripRepository aiTripRepository = mock(AiTripRepository.class);
    private final FileUploadProperties properties = properties();
    private final FileUploadServiceImpl service = new FileUploadServiceImpl(
            storageService,
            mock(RestTemplate.class),
            properties,
            new ImgpressProperties(),
            imageModerationService,
            businessConfigService,
            aiTripRepository);

    @Test
    void rejectsFileWhoseBytesDoNotMatchDeclaredImageType() {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "payload.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.uploadImage(UUID.randomUUID(), disguised))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match");
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsOversizedBatchBeforeWritingAnyObject() {
        when(businessConfigService.getInt(BusinessConfigKey.UPLOAD_MAX_BATCH_FILES))
                .thenReturn(1);
        MockMultipartFile first = new MockMultipartFile("files", "one.jpg", "image/jpeg", JPEG);
        MockMultipartFile second = new MockMultipartFile("files", "two.jpg", "image/jpeg", JPEG);

        assertThatThrownBy(() -> service.uploadImages(userUpload(), List.of(first, second)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At most 1 images");
        verifyNoInteractions(storageService);
    }

    /**
     * MOD-04: one rejected photo must not cost the user the rest of the batch, and the
     * rejection has to be distinguishable from a technical failure.
     */
    @Test
    void keepsTheRestOfTheBatchWhenOneImageIsRejectedForItsContent() {
        MockMultipartFile good = new MockMultipartFile("files", "beach.jpg", "image/jpeg", JPEG);
        MockMultipartFile bad = new MockMultipartFile("files", "bad.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1});
        when(imageModerationService.inspect(any(), any(), any(), any()))
                .thenReturn(ModerationVerdict.allowed())
                .thenReturn(ModerationVerdict.of(ModerationAction.BLOCK, ModerationCategory.SEXUAL, null, "x"));
        when(businessConfigService.getInt(BusinessConfigKey.UPLOAD_MAX_BATCH_FILES))
                .thenReturn(30);
        when(businessConfigService.getBoolean(BusinessConfigKey.IMAGE_COMPRESSION_ENABLED))
                .thenReturn(false);
        when(storageService.uploadFile(anyString(), any(), anyString(), anyLong()))
                .thenReturn("https://cdn/beach.webp");

        List<ImageUploadOutcome> outcomes = service.uploadImages(userUpload(), List.of(good, bad));

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.get(0).isAccepted()).isTrue();
        assertThat(outcomes.get(1).isAccepted()).isFalse();
        assertThat(outcomes.get(1).isContentRejection()).isTrue();
        assertThat(outcomes.get(1).rejectedCategory()).isEqualTo(ModerationCategory.SEXUAL);
    }

    /** A rejected image must never reach storage, because a stored image already has a URL. */
    @Test
    void neverStoresAnImageThatModerationRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "bad.jpg", "image/jpeg", JPEG);
        when(imageModerationService.inspect(any(), any(), any(), any()))
                .thenReturn(ModerationVerdict.of(ModerationAction.BLOCK, ModerationCategory.VIOLENCE, null, "x"));

        ImageUploadOutcome outcome = service.uploadImage(userUpload(), file);

        assertThat(outcome.isContentRejection()).isTrue();
        verifyNoInteractions(storageService);
    }

    @Test
    void uploadsValidatedMp4VideoUsingTheVideoSizeLimit() {
        MockMultipartFile video = new MockMultipartFile(
                "file",
                "memory.mp4",
                "video/mp4",
                new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});

        service.uploadVideo(UUID.randomUUID(), video);

        verify(storageService).uploadFile(anyString(), any(), eq("video/mp4"), eq(12L));
    }

    @Test
    void rejectsVideoThatExceedsConfiguredLimitBeforeWritingObject() {
        properties.setMaxVideoSize(DataSize.ofBytes(11));
        MockMultipartFile video = new MockMultipartFile(
                "file",
                "memory.mp4",
                "video/mp4",
                new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});

        assertThatThrownBy(() -> service.uploadVideo(UUID.randomUUID(), video))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("configured size limit");
        verifyNoInteractions(storageService);
    }

    @Test
    void uploadsMultipleImagesInParallelPreservingInputOrder() {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            FileUploadServiceImpl parallelService = new FileUploadServiceImpl(
                    storageService,
                    mock(RestTemplate.class),
                    properties,
                    new ImgpressProperties(),
                    imageModerationService,
                    businessConfigService,
                    aiTripRepository,
                    pool);

            when(businessConfigService.getInt(BusinessConfigKey.UPLOAD_MAX_BATCH_FILES))
                    .thenReturn(10);
            when(businessConfigService.getBoolean(BusinessConfigKey.IMAGE_COMPRESSION_ENABLED))
                    .thenReturn(false);
            when(imageModerationService.inspect(any(), any(), any(), any()))
                    .thenReturn(ModerationVerdict.allowed());
            when(storageService.uploadFile(anyString(), any(), anyString(), anyLong()))
                    .thenAnswer(invocation -> "https://cdn/" + invocation.getArgument(0));

            MockMultipartFile file1 = new MockMultipartFile("files", "img1.jpg", "image/jpeg", JPEG);
            MockMultipartFile file2 = new MockMultipartFile("files", "img2.jpg", "image/jpeg", JPEG);
            MockMultipartFile file3 = new MockMultipartFile("files", "img3.jpg", "image/jpeg", JPEG);

            List<ImageUploadOutcome> outcomes = parallelService.uploadImages(userUpload(), List.of(file1, file2, file3));

            assertThat(outcomes).hasSize(3);
            assertThat(outcomes.get(0).originalFilename()).isEqualTo("img1.jpg");
            assertThat(outcomes.get(1).originalFilename()).isEqualTo("img2.jpg");
            assertThat(outcomes.get(2).originalFilename()).isEqualTo("img3.jpg");
            assertThat(outcomes).allMatch(ImageUploadOutcome::isAccepted);
        } finally {
            pool.shutdown();
        }
    }

    private ImageUploadRequest userUpload() {
        return new ImageUploadRequest(UUID.randomUUID(),
                ImageUploadRequest.ImageEntryPoint.USER_UPLOAD, "expenses/test", false);
    }

    private FileUploadProperties properties() {
        FileUploadProperties configured = new FileUploadProperties();
        configured.setMaxImageSize(DataSize.ofMegabytes(5));
        configured.setMaxVideoSize(DataSize.ofMegabytes(50));
        return configured;
    }
}

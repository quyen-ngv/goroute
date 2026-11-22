package com.ds.goroute.service.impl;

import com.ds.goroute.config.FileUploadProperties;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ImageModerationService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final FileUploadProperties properties = properties();
    private final FileUploadServiceImpl service = new FileUploadServiceImpl(
            storageService,
            restTemplate,
            properties,
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
     * Moderation no longer sits in the upload request: the image is stored and returned at
     * once, then handed to the background check with the key and URL it was stored under.
     */
    @Test
    void storesTheImageFirstThenSubmitsItForBackgroundModeration() {
        MockMultipartFile file = new MockMultipartFile("file", "beach.jpg", "image/jpeg", JPEG);
        when(storageService.uploadFile(anyString(), any(), anyString(), anyLong()))
                .thenAnswer(invocation -> "https://cdn/" + invocation.getArgument(0));
        ImageUploadRequest request = userUpload();

        ImageUploadOutcome outcome = service.uploadImage(request, file);

        assertThat(outcome.isAccepted()).isTrue();
        assertThat(outcome.isContentRejection()).isFalse();
        InOrder order = inOrder(storageService, imageModerationService);
        order.verify(storageService).uploadFile(anyString(), any(), eq("image/jpeg"), eq((long) JPEG.length));
        order.verify(imageModerationService).submit(
                eq(request.userId()),
                eq(ImageUploadRequest.ImageEntryPoint.USER_UPLOAD),
                argThat(key -> key.startsWith("expenses/test/") && key.endsWith(".jpg")),
                eq(outcome.url()),
                eq(JPEG),
                eq("image/jpeg"));
    }

    @Test
    void aFileThatFailsValidationIsNeverSubmittedForModeration() {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "payload.jpg", "image/jpeg", "not-an-image".getBytes());

        ImageUploadOutcome outcome = service.uploadImage(userUpload(), disguised);

        assertThat(outcome.isAccepted()).isFalse();
        verifyNoInteractions(imageModerationService);
    }

    /** Upload latency: the image is stored as sent, with no call out to a re-encoding service. */
    @Test
    void storesTheImageAsUploadedWithoutCallingAnotherService() {
        MockMultipartFile file = new MockMultipartFile("file", "beach.jpg", "image/jpeg", JPEG);
        when(storageService.uploadFile(anyString(), any(), anyString(), anyLong()))
                .thenReturn("https://cdn/beach.jpg");

        ImageUploadOutcome outcome = service.uploadImage(userUpload(), file);

        assertThat(outcome.isAccepted()).isTrue();
        verify(storageService).uploadFile(
                endsWith(".jpg"), any(), eq("image/jpeg"), eq((long) JPEG.length));
        verifyNoInteractions(restTemplate);
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
    void uploadsAnM4aRecordingByItsBytesEvenWhenSentAsOctetStream() {
        byte[] m4a = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
        MockMultipartFile audio = new MockMultipartFile("file", "story.m4a", "application/octet-stream", m4a);
        when(storageService.uploadFile(any(), any(), eq("audio/mp4"), eq((long) m4a.length)))
                .thenReturn("https://cdn.example/story.m4a");

        assertThat(service.uploadAudio(UUID.randomUUID(), audio)).isEqualTo("https://cdn.example/story.m4a");
    }

    @Test
    void rejectsAudioThatIsNotM4aAacOrMp3BeforeWritingObject() {
        MockMultipartFile disguised = new MockMultipartFile("file", "story.mp3", "audio/mpeg", JPEG);

        assertThatThrownBy(() -> service.uploadAudio(UUID.randomUUID(), disguised))
                .isInstanceOf(BusinessException.class);
        verify(storageService, never()).uploadFile(any(), any(), any(), anyLong());
    }

    @Test
    void recognisesMp3AndAdtsAacHeaders() {
        assertThat(FileUploadServiceImpl.detectAudio(new byte[] {'I', 'D', '3', 4})).isNotNull();
        assertThat(FileUploadServiceImpl.detectAudio(new byte[] {(byte) 0xFF, (byte) 0xFB, 0, 0}).contentType())
                .isEqualTo("audio/mpeg");
        assertThat(FileUploadServiceImpl.detectAudio(new byte[] {(byte) 0xFF, (byte) 0xF1, 0, 0}).contentType())
                .isEqualTo("audio/aac");
        assertThat(FileUploadServiceImpl.detectAudio(new byte[] {'R', 'I', 'F', 'F'})).isNull();
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
                    imageModerationService,
                    businessConfigService,
                    aiTripRepository,
                    pool);

            when(businessConfigService.getInt(BusinessConfigKey.UPLOAD_MAX_BATCH_FILES))
                    .thenReturn(10);
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

    @Test
    void aPublicUrlIsFetchedThroughTheGuardedDownloadAndRecognisedByItsBytes() {
        when(storageService.downloadPublicFile("https://images.example.com/a"))
                .thenReturn(new StorageService.DownloadedFile(JPEG, "text/html"));
        when(storageService.uploadFile(anyString(), any(), anyString(), anyLong()))
                .thenAnswer(invocation -> "https://cdn/" + invocation.getArgument(0));

        ImageUploadOutcome outcome = service.uploadImageFromPublicUrl(new ImageUploadRequest(null,
                ImageUploadRequest.ImageEntryPoint.LOCAL_EVENT, "events/e1"), "https://images.example.com/a");

        assertThat(outcome.isAccepted()).isTrue();
        assertThat(outcome.url()).startsWith("https://cdn/events/e1/").endsWith(".jpg");
        verify(storageService).uploadFile(anyString(), any(), eq("image/jpeg"), eq((long) JPEG.length));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void aRefusedPublicUrlIsAFailureAndNothingIsStored() {
        when(storageService.downloadPublicFile("https://10.0.0.1/a"))
                .thenThrow(new IllegalArgumentException("Remote file URL must resolve to a public address"));

        ImageUploadOutcome outcome = service.uploadImageFromPublicUrl(new ImageUploadRequest(null,
                ImageUploadRequest.ImageEntryPoint.LOCAL_EVENT, "events/e1"), "https://10.0.0.1/a");

        assertThat(outcome.isAccepted()).isFalse();
        assertThat(outcome.failureMessage()).contains("public address");
        verify(storageService, never()).uploadFile(anyString(), any(), anyString(), anyLong());
        verifyNoInteractions(imageModerationService);
    }

    private ImageUploadRequest userUpload() {
        return new ImageUploadRequest(UUID.randomUUID(),
                ImageUploadRequest.ImageEntryPoint.USER_UPLOAD, "expenses/test");
    }

    private FileUploadProperties properties() {
        FileUploadProperties configured = new FileUploadProperties();
        configured.setMaxImageSize(DataSize.ofMegabytes(5));
        configured.setMaxVideoSize(DataSize.ofMegabytes(50));
        return configured;
    }
}

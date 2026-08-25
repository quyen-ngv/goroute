package com.ds.goroute.service.impl;

import com.ds.goroute.config.FileUploadProperties;
import com.ds.goroute.config.ImgpressProperties;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FileUploadServiceImplTest {

    private final StorageService storageService = mock(StorageService.class);
    private final FileUploadProperties properties = properties();
    private final FileUploadServiceImpl service = new FileUploadServiceImpl(
            storageService,
            mock(RestTemplate.class),
            properties,
            new ImgpressProperties());

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
        properties.setMaxBatchFiles(1);
        MockMultipartFile first = new MockMultipartFile(
                "files", "one.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MockMultipartFile second = new MockMultipartFile(
                "files", "two.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThatThrownBy(() -> service.uploadImages(UUID.randomUUID(), List.of(first, second)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At most 1 images");
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

        verify(storageService).uploadFile(
                anyString(), any(), eq("video/mp4"), eq(12L));
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

    private FileUploadProperties properties() {
        FileUploadProperties configured = new FileUploadProperties();
        configured.setMaxImageSize(DataSize.ofMegabytes(5));
        configured.setMaxVideoSize(DataSize.ofMegabytes(50));
        configured.setMaxBatchFiles(10);
        return configured;
    }
}

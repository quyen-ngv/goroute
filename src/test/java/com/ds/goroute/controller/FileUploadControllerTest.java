package com.ds.goroute.controller;

import com.ds.goroute.dto.response.ImageUploadBatchResponse;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.type.ModerationCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileUploadControllerTest {

    @Test
    void keepsLegacyUrlDataAndExposesPerFileMetadataSeparately() throws Exception {
        FileUploadService uploadService = mock(FileUploadService.class);
        FileUploadController controller = new FileUploadController(uploadService);
        String acceptedUrl = "https://cdn.example/receipt.webp";
        List<ImageUploadOutcome> outcomes = List.of(
                ImageUploadOutcome.accepted("receipt.jpg", acceptedUrl),
                ImageUploadOutcome.rejectedByModeration(
                        "blocked.jpg",
                        ModerationCategory.VIOLENCE,
                        "Image cannot be uploaded"));
        when(uploadService.uploadImages(any(ImageUploadRequest.class), anyList()))
                .thenReturn(outcomes);

        ResponseEntity<ImageUploadBatchResponse> response = controller.uploadMultipleFiles(
                List.<MultipartFile>of(new MockMultipartFile("files", "receipt.jpg", "image/jpeg", new byte[0])),
                UUID.randomUUID());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsExactly(acceptedUrl);
        assertThat(response.getBody().getImageObjects()).hasSize(2);
        assertThat(response.getBody().getImageObjects().get(0).url()).isEqualTo(acceptedUrl);
        assertThat(response.getBody().getImageObjects().get(1).accepted()).isFalse();

        String json = new ObjectMapper().writeValueAsString(response.getBody());
        assertThat(json).contains("\"data\":[\"" + acceptedUrl + "\"]");
        assertThat(json).contains("\"imageObjects\":[");
    }
}

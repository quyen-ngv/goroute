package com.ds.goroute.service.impl;

import com.ds.goroute.service.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialCandidateImageServiceImplTest {

    private static final String JPEG = Base64.getEncoder()
            .encodeToString(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x10, 0x20});

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StorageService storageService;
    private SocialCandidateImageServiceImpl service;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        service = new SocialCandidateImageServiceImpl(storageService);
    }

    @Test
    void storesEachPictureUnderTheJobAndReplacesItWithItsUrl() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(storageService.uploadBytes(any(), eq("social-location/" + jobId + "/cafe_1.jpg"), eq("image/jpeg")))
                .thenReturn("https://cdn.example/cafe.jpg");

        JsonNode stored = service.storePreviewImages(jobId, result("""
                [{"candidateRef": "cafe_1", "name": "Cafe", "previewImage": {"data": "%s"}},
                 {"candidateRef": "beach", "name": "Beach"}]
                """.formatted(JPEG)));

        JsonNode candidates = stored.path("extraction").path("candidates");
        assertThat(candidates.get(0).path("imageUrl").asText()).isEqualTo("https://cdn.example/cafe.jpg");
        assertThat(candidates.get(0).has("previewImage")).isFalse();
        assertThat(candidates.get(1).has("imageUrl")).isFalse();
    }

    @Test
    void dropsPicturesThatAreNotJpegWithoutLosingThePlace() throws Exception {
        String png = Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        JsonNode stored = service.storePreviewImages(UUID.randomUUID(), result("""
                [{"candidateRef": "a", "name": "A", "previewImage": {"data": "%s"}},
                 {"candidateRef": "b", "name": "B", "previewImage": {"data": "not base64 !!"}}]
                """.formatted(png)));

        JsonNode candidates = stored.path("extraction").path("candidates");
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).has("previewImage")).isFalse();
        assertThat(candidates.get(0).has("imageUrl")).isFalse();
        assertThat(candidates.get(1).has("imageUrl")).isFalse();
        verify(storageService, never()).uploadBytes(any(), any(), any());
    }

    @Test
    void keepsTheCandidateWhenStorageFails() throws Exception {
        when(storageService.uploadBytes(any(), any(), any())).thenThrow(new RuntimeException("S3 down"));

        JsonNode stored = service.storePreviewImages(UUID.randomUUID(), result("""
                [{"candidateRef": "a", "name": "A", "previewImage": {"data": "%s"}}]
                """.formatted(JPEG)));

        assertThat(stored.path("extraction").path("candidates").get(0).path("name").asText()).isEqualTo("A");
        assertThat(stored.path("extraction").path("candidates").get(0).has("imageUrl")).isFalse();
    }

    @Test
    void unsafeCandidateRefNeverReachesTheObjectKey() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(storageService.uploadBytes(any(), any(), any())).thenReturn("https://cdn.example/x.jpg");

        service.storePreviewImages(jobId, result("""
                [{"candidateRef": "../../etc", "name": "A", "previewImage": {"data": "%s"}}]
                """.formatted(JPEG)));

        verify(storageService).uploadBytes(any(), eq("social-location/" + jobId + "/------etc.jpg"), any());
    }

    @Test
    void laterReportsInheritTheStoredUrlByCandidateRef() throws Exception {
        JsonNode storedResult = result("""
                [{"candidateRef": "a", "imageUrl": "https://cdn.example/a.jpg"},
                 {"candidateRef": "b", "imageUrl": "https://cdn.example/b.jpg"}]
                """);
        JsonNode incoming = result("""
                [{"candidateRef": "a", "mapSearchStatus": "DONE"},
                 {"candidateRef": "b", "imageUrl": "https://cdn.example/new-b.jpg"},
                 {"candidateRef": "c"}]
                """);

        JsonNode merged = service.carryOverImageUrls(storedResult, incoming);

        JsonNode candidates = merged.path("extraction").path("candidates");
        assertThat(candidates.get(0).path("imageUrl").asText()).isEqualTo("https://cdn.example/a.jpg");
        assertThat(candidates.get(1).path("imageUrl").asText()).isEqualTo("https://cdn.example/new-b.jpg");
        assertThat(candidates.get(2).has("imageUrl")).isFalse();
    }

    private JsonNode result(String candidatesJson) throws Exception {
        return objectMapper.readTree("{\"extraction\": {\"candidates\": " + candidatesJson + "}}");
    }
}

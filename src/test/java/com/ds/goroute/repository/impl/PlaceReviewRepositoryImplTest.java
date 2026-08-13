package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.mapper.PlaceReviewMapper;
import com.ds.goroute.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceReviewRepositoryImplTest {
    private PlaceReviewMapper mapper;
    private StorageService storageService;
    private PlaceReviewRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        mapper = mock(PlaceReviewMapper.class);
        storageService = mock(StorageService.class);
        repository = new PlaceReviewRepositoryImpl(mapper, storageService, new ObjectMapper());
        when(storageService.extractObjectKey(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return value != null && value.toString().contains("onestudy.id.vn")
                    ? "reviews/managed.webp" : null;
        });
    }

    @Test
    void everyWriteDropsUrlsOutsideManagedStorage() {
        PlaceReview inserted = PlaceReview.builder()
                .reviewId("inserted")
                .profilePicture("https://googleusercontent.example/profile.jpg")
                .images("[\"https://googleusercontent.example/external.jpg\","
                        + "\"https://onestudy.id.vn/resource/goroute/reviews/managed.webp\"]")
                .build();
        PlaceReview updated = PlaceReview.builder()
                .reviewId("updated")
                .profilePicture("https://onestudy.id.vn/resource/goroute/reviews/profile.webp")
                .images("[\"https://unmanaged.example/photo.jpg\"]")
                .build();

        repository.insert(inserted);
        repository.updateBatch(List.of(updated));

        assertThat(inserted.getProfilePicture()).isNull();
        assertThat(inserted.getImages()).contains("onestudy.id.vn").doesNotContain("googleusercontent");
        assertThat(updated.getProfilePicture()).contains("onestudy.id.vn");
        assertThat(updated.getImages()).isEqualTo("[]");
        verify(mapper).insert(inserted);
        verify(mapper).updateBatch(List.of(updated));
    }
}

package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.UserReview;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.mapper.UserReviewMapper;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserReviewRepositoryImplTest {

    @Test
    void contributionAndUserReviewWritesKeepOnlyManagedPhotos() {
        UserReviewMapper mapper = mock(UserReviewMapper.class);
        StorageService storageService = mock(StorageService.class);
        MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
        when(mediaAssetRepository.findByEntity("USER_REVIEW", null)).thenReturn(List.of());
        when(storageService.extractObjectKey(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return value != null && value.toString().contains("onestudy.id.vn")
                    ? "user-reviews/managed.webp" : null;
        });
        UserReviewRepositoryImpl repository = new UserReviewRepositoryImpl(
                mapper, storageService, new ObjectMapper(), mediaAssetRepository);
        UserReview review = UserReview.builder()
                .photos("[\"https://external.example/photo.jpg\","
                        + "\"https://onestudy.id.vn/resource/goroute/user-reviews/photo.webp\"]")
                .build();

        repository.save(review);

        assertThat(review.getPhotos()).isNull();
        verify(mapper).insert(review);
        verify(mediaAssetRepository).insert(any(MediaAsset.class));
    }
}

package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateCityStoryRequest;
import com.ds.goroute.dto.response.CityStoryFeedResponse;
import com.ds.goroute.dto.response.CityStoryItemResponse;
import com.ds.goroute.dto.response.CityStoryLikeResponse;

import java.util.List;
import java.util.UUID;

public interface CityStoryService {
    CityStoryFeedResponse getFeed(UUID userId);

    List<CityStoryItemResponse> getStoriesForLocation(UUID locationImageId, UUID userId);

    CityStoryItemResponse createStory(UUID locationImageId, CreateCityStoryRequest request);

    void deleteStory(UUID storyId);

    CityStoryLikeResponse toggleLike(UUID userId, UUID storyId);

    void markViewed(UUID userId, UUID storyId);
}

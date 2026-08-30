package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateCityStoryRequest;
import com.ds.goroute.dto.response.CityStoryFeedResponse;
import com.ds.goroute.dto.response.CityStoryItemResponse;
import com.ds.goroute.dto.response.CityStoryLikeResponse;
import com.ds.goroute.service.CityStoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CityStoryController extends BaseController {

    private final CityStoryService cityStoryService;

    @GetMapping("/v1/api/city-stories/feed")
    public ResponseEntity<BaseResponse<CityStoryFeedResponse>> getFeed(
            @CurrentUser(required = false) UUID userId) {
        return ResponseEntity.ok(ofSucceeded(cityStoryService.getFeed(userId)));
    }

    @GetMapping("/v1/api/location-images/{locationId}/stories")
    public ResponseEntity<BaseResponse<List<CityStoryItemResponse>>> getLocationStories(
            @PathVariable UUID locationId,
            @CurrentUser(required = false) UUID userId) {
        return ResponseEntity.ok(ofSucceeded(cityStoryService.getStoriesForLocation(locationId, userId)));
    }

    @PostMapping("/v1/api/location-images/{locationId}/stories")
    @PreAuthorize("@adminAuthorization.can(authentication,'location-images','create')")
    public ResponseEntity<BaseResponse<CityStoryItemResponse>> createStory(
            @PathVariable UUID locationId,
            @Valid @RequestBody CreateCityStoryRequest request) {
        CityStoryItemResponse story = cityStoryService.createStory(locationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(story));
    }

    @DeleteMapping("/v1/api/city-stories/{storyId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'location-images','delete')")
    public ResponseEntity<BaseResponse<Void>> deleteStory(@PathVariable UUID storyId) {
        cityStoryService.deleteStory(storyId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/v1/api/city-stories/{storyId}/view")
    public ResponseEntity<BaseResponse<Void>> markViewed(
            @PathVariable UUID storyId,
            @CurrentUser UUID userId) {
        cityStoryService.markViewed(userId, storyId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/v1/api/city-stories/{storyId}/like")
    public ResponseEntity<BaseResponse<CityStoryLikeResponse>> toggleLike(
            @PathVariable UUID storyId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(cityStoryService.toggleLike(userId, storyId)));
    }

}

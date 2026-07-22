package com.ds.goroute.service;

import com.ds.goroute.dto.request.SavePlaceRequest;
import com.ds.goroute.dto.response.SavedPlaceResponse;
import com.ds.goroute.dto.response.SavedItemsOverviewResponse;
import java.util.List;
import java.util.UUID;

public interface SavedPlaceService {
    List<SavedPlaceResponse> getSavedPlaces(UUID userId, String category, String itemType, Integer page, Integer size);
    SavedItemsOverviewResponse getSavedItemsOverview(UUID userId);
    SavedPlaceResponse savePlace(UUID userId, SavePlaceRequest request);
    void unsavePlace(UUID userId, UUID savedPlaceId);
    SavedPlaceResponse updateTags(UUID userId, UUID savedPlaceId, List<String> tags);
}

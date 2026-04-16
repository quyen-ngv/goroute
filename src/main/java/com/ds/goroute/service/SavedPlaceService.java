package com.ds.goroute.service;

import com.ds.goroute.dto.request.SavePlaceRequest;
import com.ds.goroute.dto.response.SavedPlaceResponse;
import java.util.List;
import java.util.UUID;

public interface SavedPlaceService {
    List<SavedPlaceResponse> getSavedPlaces(UUID userId, String category, Integer page, Integer size);
    SavedPlaceResponse savePlace(UUID userId, SavePlaceRequest request);
    void unsavePlace(UUID savedPlaceId);
    SavedPlaceResponse updateTags(UUID savedPlaceId, List<String> tags);
}

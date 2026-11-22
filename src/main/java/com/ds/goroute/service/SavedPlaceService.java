package com.ds.goroute.service;

import com.ds.goroute.dto.request.SavePlaceRequest;
import com.ds.goroute.dto.response.RecentSavedPlaceResponse;
import com.ds.goroute.dto.response.SavedPlaceResponse;
import com.ds.goroute.dto.response.SavedItemsOverviewResponse;
import java.util.List;
import java.util.UUID;

public interface SavedPlaceService {
    List<SavedPlaceResponse> getSavedPlaces(UUID userId, String category, String itemType, Integer page, Integer size);
    SavedItemsOverviewResponse getSavedItemsOverview(UUID userId);
    List<RecentSavedPlaceResponse> getRecentSavedPlaces(UUID userId);
    SavedPlaceResponse savePlace(UUID userId, SavePlaceRequest request);
    void unsavePlace(UUID userId, UUID savedPlaceId);
    SavedPlaceResponse updateTags(UUID userId, UUID savedPlaceId, List<String> tags);

    /**
     * Takes places off the saved list: out of {@code category} only when it is set, else off
     * the list altogether, places found in the user's videos included.
     */
    void removeFromList(UUID userId, List<String> placeIds, String category);

    /** Marks a place as been there, or takes the mark off. */
    void setVisited(UUID userId, String placeId, boolean visited);
}

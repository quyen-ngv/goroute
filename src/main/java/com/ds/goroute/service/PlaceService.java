package com.ds.goroute.service;

import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.dto.response.PlaceReviewResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PlaceService {
    
    /**
     * Import place from Google Maps data
     */
    PlaceResponse importPlace(ImportPlaceRequest request);
    
    /**
     * Get place by ID
     */
    PlaceResponse getPlaceById(UUID id);
    
    /**
     * Import multiple places
     */
    List<PlaceResponse> importPlaces(List<ImportPlaceRequest> requests);
    
    /**
     * Get place by Google Place ID
     */
    PlaceResponse getPlaceByGoogleId(String placeId);
    
    /**
     * Search places by location and filters
     */
    List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude, 
                                     BigDecimal radius, String category, BigDecimal minRating, int page, int size);
    
    /**
     * Get reviews for a place
     */
    List<PlaceReviewResponse> getPlaceReviews(UUID placeId);
    
    /**
     * Update place information
     */
    PlaceResponse updatePlace(UUID id, UpdatePlaceRequest request);
    
    /**
     * Delete place and its reviews
     */
    void deletePlace(UUID id);
}

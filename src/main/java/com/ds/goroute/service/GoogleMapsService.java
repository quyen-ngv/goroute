package com.ds.goroute.service;

import com.ds.goroute.dto.response.*;
import java.util.List;

public interface GoogleMapsService {
    
    /**
     * Places Autocomplete API - Search suggestions
     */
    List<PlaceSearchResponse> searchPlaces(String query, Double lat, Double lng);
    
    /**
     * Place Details API - Get detailed information about a place
     */
    PlaceDetailResponse getPlaceDetails(String placeId);
    
    /**
     * Nearby Search API - Find places near a location
     */
    List<NearbyPlaceResponse> nearbySearch(Double lat, Double lng, Integer radius, String type);
    
    /**
     * Directions API - Calculate route between two points
     */
    RouteResponse calculateRoute(Double originLat, Double originLng, 
                                 Double destLat, Double destLng, 
                                 String travelMode);
    
    /**
     * Distance Matrix API - Calculate distances between multiple origins and destinations
     */
    DistanceMatrixResponse calculateDistanceMatrix(List<String> origins, 
                                                    List<String> destinations, 
                                                    String travelMode);
}

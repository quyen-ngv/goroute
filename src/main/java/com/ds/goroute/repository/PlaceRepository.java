package com.ds.goroute.repository;

import com.ds.goroute.entity.Place;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository {
    
    void insert(Place place);
    
    void update(Place place);
    
    Place findById(UUID id);
    
    Place findByPlaceId(String placeId);
    
    List<Place> findNearby(String keyword, BigDecimal latitude, BigDecimal longitude, BigDecimal radius, 
                          String category, BigDecimal minRating, int limit, int offset);
    
    void delete(UUID id);
}

package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Place;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.repository.PlaceRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public class PlaceRepositoryImpl implements PlaceRepository {
    
    private final PlaceMapper placeMapper;

    public PlaceRepositoryImpl(PlaceMapper placeMapper) {
        this.placeMapper = placeMapper;
    }

    @Override
    public void insert(Place place) {
        placeMapper.insert(place);
    }
    
    @Override
    public void update(Place place) {
        placeMapper.update(place);
    }
    
    @Override
    public Place findById(UUID id) {
        return placeMapper.findById(id);
    }
    
    @Override
    public Place findByPlaceId(String placeId) {
        return placeMapper.findByPlaceId(placeId);
    }
    
    @Override
    public List<Place> findNearby(String keyword, BigDecimal latitude, BigDecimal longitude, BigDecimal radius, 
                                 String category, BigDecimal minRating, int limit, int offset) {
        return placeMapper.findNearby(keyword, latitude, longitude, radius, category, minRating, limit, offset);
    }
    
    @Override
    public void delete(UUID id) {
        placeMapper.delete(id);
    }
}

package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.PlaceScore;
import com.ds.goroute.mapper.PlaceScoreMapper;
import com.ds.goroute.repository.PlaceScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaceScoreRepositoryImpl implements PlaceScoreRepository {
    private final PlaceScoreMapper mapper;
    
    @Override
    public void save(PlaceScore score) {
        mapper.insert(score);
    }
    
    @Override
    public void update(PlaceScore score) {
        mapper.update(score);
    }
    
    @Override
    public Optional<PlaceScore> findByPlaceId(UUID placeId) {
        return Optional.ofNullable(mapper.findByPlaceId(placeId));
    }
}

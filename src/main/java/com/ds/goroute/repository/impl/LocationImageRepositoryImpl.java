package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.mapper.LocationImageMapper;
import com.ds.goroute.repository.LocationImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LocationImageRepositoryImpl implements LocationImageRepository {
    
    private final LocationImageMapper locationImageMapper;
    private final com.ds.goroute.mapper.LocationImageWardMapper wardMapper;
    
    @Override
    public Optional<LocationImage> findBestMatch(String searchTerm) {
        return Optional.ofNullable(locationImageMapper.selectBestMatch(searchTerm));
    }
    
    @Override
    public List<LocationImage> findAll() {
        return locationImageMapper.selectAll();
    }
    
    @Override
    public Optional<LocationImage> findById(UUID id) {
        return Optional.ofNullable(locationImageMapper.selectById(id));
    }
    
    @Override
    public void insert(LocationImage locationImage) {
        locationImageMapper.insert(locationImage);
    }
    
    @Override
    public void update(LocationImage locationImage) {
        locationImageMapper.updateById(locationImage);
    }
    
    @Override
    public void deleteById(UUID id) {
        locationImageMapper.deleteById(id);
    }

    @Override
    public List<String> findWardCodes(UUID locationImageId) {
        return wardMapper.findWardCodes(locationImageId);
    }

    @Override
    public java.util.Map<UUID, List<String>> findAllWardCodes() {
        java.util.Map<UUID, List<String>> result = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : wardMapper.findAllLinks()) {
            result.computeIfAbsent((UUID) row.get("locationImageId"), key -> new java.util.ArrayList<>())
                    .add(row.get("wardCode").toString());
        }
        return result;
    }

    @Override
    public void replaceWardCodes(UUID locationImageId, java.util.Collection<String> wardCodes) {
        wardMapper.deleteByLocationImage(locationImageId);
        if (wardCodes != null && !wardCodes.isEmpty()) {
            wardMapper.insertAll(locationImageId, new java.util.LinkedHashSet<>(wardCodes));
        }
    }
}

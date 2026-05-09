package com.ds.goroute.repository;

import com.ds.goroute.entity.LocationImage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationImageRepository {
    Optional<LocationImage> findBestMatch(String searchTerm);
    List<LocationImage> findAll();
    Optional<LocationImage> findById(UUID id);
    void insert(LocationImage locationImage);
    void update(LocationImage locationImage);
    void deleteById(UUID id);
}

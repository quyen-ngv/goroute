package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.PlaceContributor;
import com.ds.goroute.mapper.PlaceContributionMapper;
import com.ds.goroute.repository.PlaceContributorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaceContributorRepositoryImpl implements PlaceContributorRepository {

    private final PlaceContributionMapper contributionMapper;

    @Override
    public void credit(UUID placeId, UUID userId) {
        contributionMapper.insertContributor(PlaceContributor.builder()
                .placeId(placeId)
                .userId(userId)
                .contributionId(null)
                .createdAt(LocalDateTime.now())
                .build());
    }
}

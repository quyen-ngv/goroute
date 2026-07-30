package com.ds.goroute.repository.impl;

import com.ds.goroute.mapper.PlaceSourceMapper;
import com.ds.goroute.repository.PlaceSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaceSourceRepositoryImpl implements PlaceSourceRepository {
    private final PlaceSourceMapper mapper;

    @Override
    public UUID upsertGoogleSource(UUID sourceId, UUID placeId, String externalId, String externalUrl,
                                   String cid, String dataId, String payloadChecksum, LocalDateTime observedAt) {
        return mapper.upsertGoogleSource(sourceId, placeId, externalId, externalUrl, cid, dataId,
                payloadChecksum, observedAt);
    }

    @Override
    public int insertSnapshot(UUID snapshotId, UUID sourceId, String payload, String payloadChecksum,
                              LocalDateTime observedAt) {
        return mapper.insertSnapshot(snapshotId, sourceId, payload, payloadChecksum, observedAt);
    }

    @Override
    public String findPrimarySourceType(UUID placeId) {
        return mapper.findPrimarySourceType(placeId);
    }

    @Override
    public int attachGoogleIdentity(UUID placeId, String googlePlaceId, String cid, String dataId,
                                    String googleMapsLink, LocalDateTime updatedAt) {
        return mapper.attachGoogleIdentity(placeId, googlePlaceId, cid, dataId, googleMapsLink, updatedAt);
    }
}

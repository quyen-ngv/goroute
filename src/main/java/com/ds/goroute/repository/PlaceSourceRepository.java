package com.ds.goroute.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface PlaceSourceRepository {
    UUID upsertGoogleSource(UUID sourceId, UUID placeId, String externalId, String externalUrl,
                            String cid, String dataId, String payloadChecksum, LocalDateTime observedAt);

    int insertSnapshot(UUID snapshotId, UUID sourceId, String payload, String payloadChecksum,
                       LocalDateTime observedAt);

    String findPrimarySourceType(UUID placeId);

    int attachGoogleIdentity(UUID placeId, String googlePlaceId, String cid, String dataId,
                             String googleMapsLink, LocalDateTime updatedAt);
}

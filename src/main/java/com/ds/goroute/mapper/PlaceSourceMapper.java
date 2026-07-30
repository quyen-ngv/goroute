package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper
public interface PlaceSourceMapper {
    UUID upsertGoogleSource(@Param("sourceId") UUID sourceId, @Param("placeId") UUID placeId,
                            @Param("externalId") String externalId, @Param("externalUrl") String externalUrl,
                            @Param("cid") String cid, @Param("dataId") String dataId,
                            @Param("payloadChecksum") String payloadChecksum,
                            @Param("observedAt") LocalDateTime observedAt);

    int insertSnapshot(@Param("snapshotId") UUID snapshotId, @Param("sourceId") UUID sourceId,
                       @Param("payload") String payload, @Param("payloadChecksum") String payloadChecksum,
                       @Param("observedAt") LocalDateTime observedAt);

    String findPrimarySourceType(@Param("placeId") UUID placeId);

    int attachGoogleIdentity(@Param("placeId") UUID placeId, @Param("googlePlaceId") String googlePlaceId,
                             @Param("cid") String cid, @Param("dataId") String dataId,
                             @Param("googleMapsLink") String googleMapsLink,
                             @Param("updatedAt") LocalDateTime updatedAt);
}

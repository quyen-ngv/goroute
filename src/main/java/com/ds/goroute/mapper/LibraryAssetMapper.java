package com.ds.goroute.mapper;

import com.ds.goroute.entity.LibraryAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface LibraryAssetMapper {

    void insert(LibraryAsset asset);

    int update(LibraryAsset asset);

    int delete(@Param("id") UUID id);

    LibraryAsset findById(@Param("id") UUID id);

    LibraryAsset findByCollectionAndKey(@Param("collection") String collection, @Param("assetKey") String assetKey);

    List<LibraryAsset> findByIds(@Param("ids") Collection<UUID> ids);

    List<LibraryAsset> search(@Param("collection") String collection,
                              @Param("status") String status,
                              @Param("q") String q,
                              @Param("missingImage") boolean missingImage,
                              @Param("limit") int limit,
                              @Param("offset") int offset);

    long count(@Param("collection") String collection,
               @Param("status") String status,
               @Param("q") String q,
               @Param("missingImage") boolean missingImage);
}

package com.ds.goroute.mapper;

import com.ds.goroute.entity.CameraLook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface CameraLookMapper {

    void insert(CameraLook look);

    int update(CameraLook look);

    int updateSortOrder(@Param("id") UUID id,
                        @Param("kind") String kind,
                        @Param("sortOrder") int sortOrder,
                        @Param("updatedBy") UUID updatedBy,
                        @Param("updatedAt") LocalDateTime updatedAt);

    int delete(@Param("id") UUID id);

    CameraLook findById(@Param("id") UUID id);

    CameraLook findByKindAndKey(@Param("kind") String kind, @Param("lookKey") String lookKey);

    List<CameraLook> search(@Param("kind") String kind,
                            @Param("status") String status,
                            @Param("q") String q,
                            @Param("limit") int limit);

    List<CameraLook> findActive(@Param("now") LocalDateTime now, @Param("limit") int limit);
}

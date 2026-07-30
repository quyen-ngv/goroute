package com.ds.goroute.mapper;

import com.ds.goroute.entity.AppConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface AppConfigMapper {
    int insert(AppConfig config);
    int update(AppConfig config);
    int delete(@Param("id") UUID id);
    AppConfig findById(@Param("id") UUID id);
    AppConfig findActiveByLabelAndKey(@Param("label") String label, @Param("key") String key);
    List<AppConfig> findAdmin(@Param("query") String query, @Param("label") String label,
                              @Param("active") Boolean active, @Param("limit") int limit,
                              @Param("offset") int offset);
}

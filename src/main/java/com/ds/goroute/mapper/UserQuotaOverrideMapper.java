package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserQuotaOverride;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface UserQuotaOverrideMapper {
    UserQuotaOverride findByUserId(@Param("userId") UUID userId);

    int insert(UserQuotaOverride override);

    int update(UserQuotaOverride override);
}

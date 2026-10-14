package com.ds.goroute.mapper;

import com.ds.goroute.dto.request.AiApiCallFilter;
import com.ds.goroute.dto.response.AiApiCallStatResponse;
import com.ds.goroute.entity.AiApiCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AiApiCallMapper {
    void upsert(AiApiCall call);

    List<AiApiCall> search(@Param("f") AiApiCallFilter filter, @Param("limit") int limit, @Param("offset") int offset);

    long count(@Param("f") AiApiCallFilter filter);

    AiApiCall findById(@Param("id") UUID id);

    List<AiApiCallStatResponse> stats(@Param("f") AiApiCallFilter filter);

    List<String> distinctFeatures();

    List<String> distinctModels();

    /** Successful calls one user made for one feature since a point in time (a daily allowance ledger). */
    long countSucceededSince(@Param("userId") UUID userId, @Param("feature") String feature,
                             @Param("since") LocalDateTime since);
}

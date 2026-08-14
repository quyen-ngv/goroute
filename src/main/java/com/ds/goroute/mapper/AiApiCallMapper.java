package com.ds.goroute.mapper;

import com.ds.goroute.entity.AiApiCall;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiApiCallMapper {
    void upsert(AiApiCall call);
}

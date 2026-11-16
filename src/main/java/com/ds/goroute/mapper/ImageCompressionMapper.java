package com.ds.goroute.mapper;

import com.ds.goroute.entity.ImageCompressionGroupTotal;
import com.ds.goroute.entity.ImageCompressionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImageCompressionMapper {

    int upsert(@Param("record") ImageCompressionRecord record);

    List<String> findRecordedKeys(@Param("keys") List<String> keys);

    List<ImageCompressionGroupTotal> totalsByGroup();
}

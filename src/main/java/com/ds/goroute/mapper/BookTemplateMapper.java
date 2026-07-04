package com.ds.goroute.mapper;

import com.ds.goroute.entity.BookTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BookTemplateMapper {
    BookTemplate selectByTypeAndRefId(@Param("templateType") String templateType, @Param("refId") String refId);
}

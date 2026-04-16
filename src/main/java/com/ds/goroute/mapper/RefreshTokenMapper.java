package com.ds.goroute.mapper;

import com.ds.goroute.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface RefreshTokenMapper {
    int insert(RefreshToken token);
    
    RefreshToken selectByToken(@Param("token") String token);
    
    RefreshToken selectById(@Param("id") UUID id);
    
    int deleteByToken(@Param("token") String token);
    
    int deleteById(@Param("id") UUID id);
    
    int deleteByUserId(@Param("userId") UUID userId);
}

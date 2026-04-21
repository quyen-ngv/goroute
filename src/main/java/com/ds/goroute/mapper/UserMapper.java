package com.ds.goroute.mapper;

import com.ds.goroute.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface UserMapper {
    int insert(User user);
    
    User selectByEmail(@Param("email") String email);
    
    User selectByUsername(@Param("username") String username);
    
    User selectByProviderId(@Param("providerId") String providerId);
    
    User selectById(@Param("id") UUID id);
    
    java.util.List<User> selectAll();
    
    int updateById(User user);
    
    int deleteById(@Param("id") UUID id);
    
    int softDeleteById(@Param("id") UUID id);
}

package com.ds.goroute.mapper;

import com.ds.goroute.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface UserMapper {
    int insert(User user);
    
    User selectByEmail(@Param("email") String email);
    
    User selectByEmailIncludingDeleted(@Param("email") String email);
    
    User selectByUsername(@Param("username") String username);
    
    User selectByProviderId(@Param("providerId") String providerId);
    
    User selectById(@Param("id") UUID id);
    
    java.util.List<User> selectAll();

    java.util.List<User> selectFollowers(@Param("userId") UUID userId);

    java.util.List<User> selectFollowing(@Param("userId") UUID userId);

    int countFollowers(@Param("userId") UUID userId);

    int countFollowing(@Param("userId") UUID userId);

    int insertFollow(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    int deleteFollow(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    boolean existsFollow(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);
    
    int updateById(User user);

    int updateLastLoginAt(@Param("id") UUID id);
    
    int deleteById(@Param("id") UUID id);
    
    int softDeleteById(@Param("id") UUID id);
}

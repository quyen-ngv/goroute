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
    
    java.util.List<User> selectByIds(@Param("ids") java.util.Collection<UUID> ids);

    java.util.List<User> selectAll();

    java.util.List<User> selectFollowers(@Param("userId") UUID userId);

    java.util.List<User> selectFollowing(@Param("userId") UUID userId);

    int countFollowers(@Param("userId") UUID userId);

    int countFollowing(@Param("userId") UUID userId);

    /**
     * Discover page and the two follow lists all render the same card. These three read
     * the page and its four counts in one statement instead of one profile query plus
     * four counts per row.
     */
    java.util.List<com.ds.goroute.dto.response.DiscoverUserResponse> selectDiscoverUsers(
            @Param("userId") UUID userId, @Param("limit") int limit);

    java.util.List<com.ds.goroute.dto.response.DiscoverUserResponse> selectFollowerProfiles(
            @Param("userId") UUID userId);

    java.util.List<com.ds.goroute.dto.response.DiscoverUserResponse> selectFollowingProfiles(
            @Param("userId") UUID userId);

    int insertFollow(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    int deleteFollow(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    boolean existsFollow(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);
    
    int updateById(User user);

    int updateLastLoginAt(@Param("id") UUID id);
    
    int deleteById(@Param("id") UUID id);
    
    int softDeleteById(@Param("id") UUID id);

    int updatePassword(@Param("id") UUID id,
                       @Param("passwordHash") String passwordHash,
                       @Param("mustChangePassword") boolean mustChangePassword,
                       @Param("changedAt") java.time.LocalDateTime changedAt);
}

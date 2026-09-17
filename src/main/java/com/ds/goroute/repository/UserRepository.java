package com.ds.goroute.repository;

import com.ds.goroute.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    void insert(User user);
    
    Optional<User> findById(UUID id);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByEmailIncludingDeleted(String email);
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByProviderId(String providerId);
    
    /** Batch form of {@link #findById}. Ids not found — or soft-deleted — are simply absent. */
    List<User> findByIds(java.util.Collection<UUID> ids);

    List<User> findAll();

    List<User> findFollowers(UUID userId);

    List<User> findFollowing(UUID userId);

    int countFollowers(UUID userId);

    int countFollowing(UUID userId);

    /** Discover page: the top {@code limit} candidates plus their four counts, in one query. */
    List<com.ds.goroute.dto.response.DiscoverUserResponse> findDiscoverUsers(UUID userId, int limit);

    /** {@link #findFollowers} plus the four counts each card shows, in one query. */
    List<com.ds.goroute.dto.response.DiscoverUserResponse> findFollowerProfiles(UUID userId);

    /** {@link #findFollowing} plus the four counts each card shows, in one query. */
    List<com.ds.goroute.dto.response.DiscoverUserResponse> findFollowingProfiles(UUID userId);

    void follow(UUID followerId, UUID followingId);

    void unfollow(UUID followerId, UUID followingId);

    boolean isFollowing(UUID followerId, UUID followingId);
    
    void updateById(User user);

    void updateLastLoginAt(UUID id);
    
    void update(User user);
    
    void deleteById(UUID id);
    
    void softDeleteById(UUID id);

    int updatePassword(UUID id, String passwordHash, boolean mustChangePassword, java.time.LocalDateTime changedAt);
}

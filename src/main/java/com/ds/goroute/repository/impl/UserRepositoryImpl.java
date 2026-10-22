package com.ds.goroute.repository.impl;

import com.ds.goroute.config.cache.AuthSubjectCache;
import com.ds.goroute.entity.User;
import com.ds.goroute.mapper.UserMapper;
import com.ds.goroute.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    
    private final UserMapper userMapper;
    /** Account existence and must_change_password feed the JWT filter's cached subject. */
    private final AuthSubjectCache authSubjectCache;

    @Override
    public void insert(User user) {
        userMapper.insert(user);
        authSubjectCache.evict(user.getId());
    }
    
    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }
    
    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(userMapper.selectByEmail(email));
    }
    
    @Override
    public Optional<User> findByEmailIncludingDeleted(String email) {
        return Optional.ofNullable(userMapper.selectByEmailIncludingDeleted(email));
    }
    
    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectByUsername(username));
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }
    
    @Override
    public Optional<User> findByProviderId(String providerId) {
        return Optional.ofNullable(userMapper.selectByProviderId(providerId));
    }
    
    @Override
    public List<User> findByIds(java.util.Collection<UUID> ids) {
        // An empty IN () is a syntax error, and the answer is knowable here.
        if (ids == null || ids.isEmpty()) return List.of();
        return userMapper.selectByIds(ids);
    }

    @Override
    public List<User> findAll() {
        return userMapper.selectAll();
    }

    @Override
    public List<User> findFollowers(UUID userId) {
        return userMapper.selectFollowers(userId);
    }

    @Override
    public List<User> findFollowing(UUID userId) {
        return userMapper.selectFollowing(userId);
    }

    @Override
    public int countFollowers(UUID userId) {
        return userMapper.countFollowers(userId);
    }

    @Override
    public int countFollowing(UUID userId) {
        return userMapper.countFollowing(userId);
    }

    @Override
    public List<com.ds.goroute.dto.response.DiscoverUserResponse> findDiscoverUsers(UUID userId, int limit) {
        return userMapper.selectDiscoverUsers(userId, limit);
    }

    @Override
    public List<com.ds.goroute.dto.response.DiscoverUserResponse> searchUsers(UUID viewerId, String query, int limit) {
        return userMapper.selectSearchUsers(viewerId, query, limit);
    }

    @Override
    public List<com.ds.goroute.dto.response.TopActiveUserResponse> findTopActiveUsers(
            UUID viewerId, java.time.LocalDateTime since, int checkinPoints, int checkinWithReviewPoints,
            int minTripActivities, int maxTripPoints, int limit) {
        return userMapper.selectTopActiveUsers(viewerId, since, checkinPoints, checkinWithReviewPoints,
                minTripActivities, maxTripPoints, limit);
    }

    @Override
    public List<com.ds.goroute.dto.response.DiscoverUserResponse> findFollowerProfiles(UUID userId) {
        return userMapper.selectFollowerProfiles(userId);
    }

    @Override
    public List<com.ds.goroute.dto.response.DiscoverUserResponse> findFollowingProfiles(UUID userId) {
        return userMapper.selectFollowingProfiles(userId);
    }

    @Override
    public void follow(UUID followerId, UUID followingId) {
        userMapper.insertFollow(followerId, followingId);
    }

    @Override
    public void unfollow(UUID followerId, UUID followingId) {
        userMapper.deleteFollow(followerId, followingId);
    }

    @Override
    public boolean isFollowing(UUID followerId, UUID followingId) {
        return userMapper.existsFollow(followerId, followingId);
    }

    @Override
    public List<UUID> findNotifiableFollowerIds(UUID authorId, UUID afterFollowerId,
                                                java.util.Collection<String> betaUsernames, int limit) {
        if (betaUsernames != null && betaUsernames.isEmpty()) {
            return List.of();
        }
        return userMapper.selectFollowerIdChunk(authorId, afterFollowerId, betaUsernames, limit);
    }    
    @Override
    public void updateById(User user) {
        userMapper.updateById(user);
        authSubjectCache.evict(user.getId());
    }

    @Override
    public void updateLastLoginAt(UUID id) {
        userMapper.updateLastLoginAt(id);
    }
    
    @Override
    public void update(User user) {
        userMapper.updateById(user);
        authSubjectCache.evict(user.getId());
    }

    @Override
    public void deleteById(UUID id) {
        userMapper.deleteById(id);
        authSubjectCache.evict(id);
    }

    @Override
    public void softDeleteById(UUID id) {
        userMapper.softDeleteById(id);
        authSubjectCache.evict(id);
    }

    @Override
    public int updatePassword(UUID id, String passwordHash, boolean mustChangePassword,
                              java.time.LocalDateTime changedAt) {
        int updated = userMapper.updatePassword(id, passwordHash, mustChangePassword, changedAt);
        authSubjectCache.evict(id);
        return updated;
    }

    @Override
    public int updateBirthYear(UUID id, int birthYear, java.time.LocalDateTime updatedAt) {
        return userMapper.updateBirthYear(id, birthYear, updatedAt);
    }

    @Override
    public List<com.ds.goroute.dto.response.DiscoverUserResponse> findDiscoverProfilesByIds(
            java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectDiscoverProfilesByIds(ids);
    }

    @Override
    public List<com.ds.goroute.entity.SocialSearchIndexRow> findSearchIndexPage(UUID afterId, int limit) {
        return userMapper.selectSearchIndexPage(afterId, limit);
    }

    @Override
    public List<com.ds.goroute.entity.SocialSearchIndexRow> findSearchIndexChanges(
            java.time.LocalDateTime afterUpdatedAt, UUID afterId, int limit) {
        return userMapper.selectSearchIndexChanges(afterUpdatedAt, afterId, limit);
    }

    @Override
    public Optional<java.time.LocalDateTime> findSearchIndexMaxUpdatedAt() {
        return Optional.ofNullable(userMapper.selectSearchIndexMaxUpdatedAt());
    }
}

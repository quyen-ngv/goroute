package com.ds.goroute.repository.impl;

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
    
    @Override
    public void insert(User user) {
        userMapper.insert(user);
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
    public Optional<User> findByProviderId(String providerId) {
        return Optional.ofNullable(userMapper.selectByProviderId(providerId));
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
    public void updateById(User user) {
        userMapper.updateById(user);
    }

    @Override
    public void updateLastLoginAt(UUID id) {
        userMapper.updateLastLoginAt(id);
    }
    
    @Override
    public void update(User user) {
        userMapper.updateById(user);
    }
    
    @Override
    public void deleteById(UUID id) {
        userMapper.deleteById(id);
    }
    
    @Override
    public void softDeleteById(UUID id) {
        userMapper.softDeleteById(id);
    }
}

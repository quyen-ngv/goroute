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
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectByUsername(username));
    }
    
    @Override
    public List<User> findAll() {
        return userMapper.selectAll();
    }
    
    @Override
    public void updateById(User user) {
        userMapper.updateById(user);
    }
    
    @Override
    public void deleteById(UUID id) {
        userMapper.deleteById(id);
    }
}

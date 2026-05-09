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
    
    List<User> findAll();
    
    void updateById(User user);
    
    void update(User user);
    
    void deleteById(UUID id);
    
    void softDeleteById(UUID id);
}

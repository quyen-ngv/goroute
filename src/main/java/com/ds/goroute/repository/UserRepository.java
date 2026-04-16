package com.ds.goroute.repository;

import com.ds.goroute.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    void insert(User user);
    
    Optional<User> findById(UUID id);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByUsername(String username);
    
    List<User> findAll();
    
    void updateById(User user);
    
    void deleteById(UUID id);
}

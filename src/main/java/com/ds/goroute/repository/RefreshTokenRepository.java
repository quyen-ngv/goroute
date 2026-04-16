package com.ds.goroute.repository;

import com.ds.goroute.entity.RefreshToken;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    void insert(RefreshToken token);
    
    Optional<RefreshToken> findByToken(String token);
    
    Optional<RefreshToken> findById(UUID id);
    
    void deleteByToken(String token);
    
    void deleteById(UUID id);
}

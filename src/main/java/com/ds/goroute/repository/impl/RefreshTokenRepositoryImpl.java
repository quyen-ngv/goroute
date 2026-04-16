package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.RefreshToken;
import com.ds.goroute.mapper.RefreshTokenMapper;
import com.ds.goroute.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {
    
    private final RefreshTokenMapper refreshTokenMapper;
    
    @Override
    public void insert(RefreshToken token) {
        refreshTokenMapper.insert(token);
    }
    
    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return Optional.ofNullable(refreshTokenMapper.selectByToken(token));
    }
    
    @Override
    public Optional<RefreshToken> findById(UUID id) {
        return Optional.ofNullable(refreshTokenMapper.selectById(id));
    }
    
    @Override
    public void deleteByToken(String token) {
        refreshTokenMapper.deleteByToken(token);
    }
    
    @Override
    public void deleteById(UUID id) {
        refreshTokenMapper.deleteById(id);
    }
}

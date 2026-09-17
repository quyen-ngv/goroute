package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.RefreshToken;
import com.ds.goroute.mapper.RefreshTokenMapper;
import com.ds.goroute.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh tokens are stored as a SHA-256 digest, so a dump of {@code refresh_tokens} no
 * longer hands over usable credentials. The token is a 128-bit random UUID, so a plain
 * digest is enough — there is nothing to guess and nothing to salt.
 *
 * <p>Rows written before this change hold the raw token. Every lookup therefore tries the
 * digest first and the raw value second, so existing sessions keep working and convert to
 * the hashed form on their next refresh.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    private final RefreshTokenMapper refreshTokenMapper;

    @Override
    public void insert(RefreshToken token) {
        // Insert a copy so the caller keeps the raw value it has to return to the client.
        refreshTokenMapper.insert(RefreshToken.builder()
                .id(token.getId())
                .userId(token.getUserId())
                .token(digest(token.getToken()))
                .expiresAt(token.getExpiresAt())
                .createdAt(token.getCreatedAt())
                .build());
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(refreshTokenMapper.selectByAnyToken(digest(token), token));
    }

    @Override
    public Optional<RefreshToken> findById(UUID id) {
        return Optional.ofNullable(refreshTokenMapper.selectById(id));
    }

    @Override
    public void deleteByToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        refreshTokenMapper.deleteByAnyToken(digest(token), token);
    }

    @Override
    public void deleteById(UUID id) {
        refreshTokenMapper.deleteById(id);
    }

    @Override
    public void expireById(UUID id, LocalDateTime expiresAt) {
        if (id == null || expiresAt == null) {
            return;
        }
        refreshTokenMapper.expireById(id, expiresAt);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        if (userId == null) {
            return;
        }
        refreshTokenMapper.deleteByUserId(userId);
    }

    private static String digest(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to store refresh tokens", e);
        }
    }
}

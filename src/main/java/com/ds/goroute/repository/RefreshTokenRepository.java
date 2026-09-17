package com.ds.goroute.repository;

import com.ds.goroute.entity.RefreshToken;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    void insert(RefreshToken token);

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findById(UUID id);

    void deleteByToken(String token);

    void deleteById(UUID id);

    /**
     * Brings a token's expiry forward. Used to retire a refresh token once it has been
     * exchanged; never pushes an expiry further out.
     */
    void expireById(UUID id, LocalDateTime expiresAt);

    /**
     * Drops every refresh token a user holds, so nothing issued before this call can be
     * exchanged again. Call it wherever the account's credentials stop being the ones the
     * outstanding sessions were issued against — a password change, a password reset, or
     * account deletion — since the access token alone expires in a day but a refresh
     * token lives for thirty.
     */
    void deleteByUserId(UUID userId);
}

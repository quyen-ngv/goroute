package com.ds.goroute.service;

import com.ds.goroute.config.LoginLockoutProperties;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Counts wrong passwords per account for the app and console password logins.
 *
 * <p>Kept in memory: the backend runs as one instance by decision, and a restart only
 * forgives a few guesses. Only existing accounts are counted -- an unknown identifier has
 * nothing to protect, and counting it would let anyone fill the table. The count is cleared
 * by a correct password; while locked the password is not even checked, so the lock ends
 * {@code lockDuration} after the last failure.
 */
@Component
@Slf4j
public class LoginAttemptGuard {

    private final LoginLockoutProperties properties;
    private final Cache<UUID, Integer> failures;

    public LoginAttemptGuard(LoginLockoutProperties properties) {
        this.properties = properties;
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(properties.getLockDuration())
                .maximumSize(properties.getMaxTrackedAccounts())
                .build();
    }

    /** Refuses the attempt with 429 while the account is locked. */
    public void requireNotLocked(UUID userId) {
        if (!properties.isEnabled()) {
            return;
        }
        Integer count = failures.getIfPresent(userId);
        if (count != null && count >= properties.getMaxFailures()) {
            throw new BusinessException(ErrorConstant.LOGIN_TEMPORARILY_LOCKED);
        }
    }

    public void recordFailure(UUID userId) {
        if (!properties.isEnabled()) {
            return;
        }
        int count = failures.asMap().merge(userId, 1, Integer::sum);
        if (count == properties.getMaxFailures()) {
            log.warn("Password login locked after {} failures: userId={}", count, userId);
        }
    }

    public void recordSuccess(UUID userId) {
        failures.invalidate(userId);
    }
}

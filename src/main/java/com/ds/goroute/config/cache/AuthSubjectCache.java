package com.ds.goroute.config.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * What {@code JwtAuthenticationFilter} needs to know about the user behind a token: whether
 * the account still exists, holds an admin role, belongs to a partner organization, and must
 * change its password. Reading that took three queries on every authenticated request; the
 * app fires a burst of requests per screen, so it is kept here for {@link #TTL} instead.
 *
 * <p>Local, like every cache in this backend (it runs as one instance). Each write that can
 * change one of the four answers calls {@link #evict}: user update, soft delete and password
 * change in {@code UserRepositoryImpl}, organization and membership writes in
 * {@code HostOrganizationRepositoryImpl}, and the admin role writes. The TTL bounds the one
 * change no write announces: a partner membership whose validity window opens or closes.
 *
 * <p>Holds no repository itself (the filter passes the loader), so the repositories that
 * evict can depend on it without a cycle.
 */
@Component
public class AuthSubjectCache {

    /** Longest a revoked role or a deleted account can outlive its write when no eviction ran. */
    static final Duration TTL = Duration.ofSeconds(60);
    static final long MAX_USERS = 50_000;

    /** The authorities a token is worth; absent from the cache's value means no live account. */
    public record AuthSubject(boolean admin, boolean partner, boolean mustChangePassword) {
    }

    private final Cache<UUID, Optional<AuthSubject>> subjects = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_USERS)
            .build();

    /** The cached subject, or the loader's answer (an empty Optional for a gone account). */
    public Optional<AuthSubject> get(UUID userId, Function<UUID, Optional<AuthSubject>> loader) {
        return subjects.get(userId, loader);
    }

    /**
     * Forgets the user now and again when the surrounding transaction ends: a request that
     * reads between the write and its commit would otherwise put the old answer back for
     * the whole TTL.
     */
    public void evict(UUID userId) {
        if (userId == null) {
            return;
        }
        subjects.invalidate(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    subjects.invalidate(userId);
                }
            });
        }
    }
}

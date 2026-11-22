package com.ds.goroute.service.impl;

import com.ds.goroute.entity.User;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BetaAccessServiceImpl implements BetaAccessService {

    /**
     * How long a user's username is remembered. Home and Feed ask "is this a beta user?" several
     * times per screen (events, meetups, feed, assistant...), each a user lookup before this.
     * The list itself is never cached here, so an edit to it applies at once; only a renamed
     * account can be judged by its old name, for at most this long.
     */
    static final Duration USERNAME_TTL = Duration.ofMinutes(2);

    private final BetaUserStore store;
    private final UserRepository users;
    private final Cache<UUID, Optional<String>> usernames = Caffeine.newBuilder()
            .expireAfterWrite(USERNAME_TTL)
            .maximumSize(50_000)
            .build();

    @Override
    public boolean isBetaUser(UUID userId) {
        if (userId == null) return false;
        Set<String> allowed = store.usernames();
        // Checked before the user lookup: the list is empty far more often than not, and
        // an empty list cannot contain anybody.
        if (allowed.isEmpty()) return false;
        return usernames.get(userId, id -> users.findById(id)
                        .map(User::getUsername)
                        .filter(username -> !username.isBlank())
                        .map(username -> username.trim().toLowerCase(Locale.ROOT)))
                .map(allowed::contains)
                .orElse(false);
    }

    @Override
    public Set<String> betaUsernames() {
        return store.usernames();
    }
}

package com.ds.goroute.feed.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Rankings in process memory: 30 minutes after the last page read, at most 20,000 at once.
 * A session holds at most 500 keys (about 10 KB), so the cap bounds the memory. A restart
 * loses them; the next page then starts a new ranking (the app keeps what it shows).
 */
@Component
public class CaffeineFeedSessionStore implements FeedSessionStore {

    private final Cache<String, FeedSession> sessions = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(20_000)
            .build();

    @Override
    public void put(FeedSession session) {
        sessions.put(session.id(), session);
    }

    @Override
    public Optional<FeedSession> get(String sessionId) {
        return Optional.ofNullable(sessionId == null ? null : sessions.getIfPresent(sessionId));
    }
}

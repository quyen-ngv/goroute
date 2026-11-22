package com.ds.goroute.feed.session;

import java.util.Optional;

/**
 * Where rankings live while scrolled (design §3.5). In memory today: the backend runs as one
 * instance. A Redis-backed store implements the same interface when that changes (§6, T3).
 */
public interface FeedSessionStore {

    void put(FeedSession session);

    Optional<FeedSession> get(String sessionId);
}

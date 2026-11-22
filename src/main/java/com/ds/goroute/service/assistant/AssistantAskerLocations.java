package com.ds.goroute.service.assistant;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The asker's device position for the turns still being answered, held in memory only: never in
 * a table, a chat message or a log. A turn's position is dropped when the turn ends and, at the
 * latest, after {@link #TTL}; a restart loses them all, and those turns answer as if the app had
 * sent no position.
 *
 * <p>In memory is enough because the backend runs as one instance and the worker's tool
 * callbacks reach the same process that took the question.
 */
@Component
public class AssistantAskerLocations {

    /** Longer than a question waits and runs; a position older than this is no longer "here". */
    static final Duration TTL = Duration.ofMinutes(30);
    /** Far above the turns open at once; when reached, new positions are not kept. */
    static final int MAX_ENTRIES = 5000;

    private record Entry(AssistantAskerLocation location, long expiresAtMillis) {
    }

    private final ConcurrentHashMap<UUID, Entry> byTurn = new ConcurrentHashMap<>();
    private volatile LongSupplier clockMillis = System::currentTimeMillis;

    /** Tests only: a clock they can move. One implicit constructor keeps the bean wiring unambiguous. */
    void useClock(LongSupplier clock) {
        this.clockMillis = clock;
    }

    public void remember(UUID turnId, AssistantAskerLocation location) {
        if (turnId == null || location == null) return;
        long now = clockMillis.getAsLong();
        byTurn.values().removeIf(entry -> entry.expiresAtMillis() <= now);
        if (byTurn.size() >= MAX_ENTRIES) return;
        byTurn.put(turnId, new Entry(location, now + TTL.toMillis()));
    }

    public Optional<AssistantAskerLocation> find(UUID turnId) {
        if (turnId == null) return Optional.empty();
        Entry entry = byTurn.get(turnId);
        if (entry == null) return Optional.empty();
        if (entry.expiresAtMillis() <= clockMillis.getAsLong()) {
            byTurn.remove(turnId);
            return Optional.empty();
        }
        return Optional.of(entry.location());
    }

    public void forget(UUID turnId) {
        if (turnId != null) byTurn.remove(turnId);
    }

    int size() {
        return byTurn.size();
    }
}

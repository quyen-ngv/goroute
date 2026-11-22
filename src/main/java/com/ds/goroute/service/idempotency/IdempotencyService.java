package com.ds.goroute.service.idempotency;

import com.ds.goroute.entity.IdempotencyRecord;
import com.ds.goroute.mapper.IdempotencyRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The Idempotency-Key state machine behind {@link com.ds.goroute.config.filter.IdempotencyFilter}.
 *
 * <p>One row per (user, key). The first request claims it IN_PROGRESS, runs, and stores its
 * response (COMPLETED) or deletes the row when the response is not worth replaying (5xx, an
 * exception, a body too large to keep). A repeat with the same fingerprint replays the stored
 * response; a repeat while the first is still running is refused as in progress; the same
 * key on a different request is refused as a mismatch.
 *
 * <p>Every statement runs outside any transaction, so each one commits on its own: the claim
 * must be visible to a concurrent retry before the request it guards starts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    /**
     * How long a key is remembered; the claim and the purge job both read it. The app's offline
     * queue can hold a write for days (a trip abroad without data), and a resend after the key
     * was forgotten would run a second time, so this outlasts any realistic offline stretch.
     */
    public static final Duration TTL = Duration.ofDays(30);

    /**
     * An IN_PROGRESS claim older than this was left by a process that died mid-request, so a
     * retry may take it over instead of waiting out the whole TTL. Far above any legitimate
     * request (statements time out after 30 seconds).
     */
    public static final Duration STALE_LOCK = Duration.ofMinutes(5);

    /** Largest response body kept for replay; a larger one releases the key instead. */
    public static final int MAX_STORED_BODY_BYTES = 1024 * 1024;

    /** Rows deleted per statement by {@link #purgeExpired()}. */
    static final int PURGE_BATCH_SIZE = 1000;

    /** Batches per purge run, so one run stays bounded even after a long outage. */
    static final int PURGE_MAX_BATCHES = 100;

    private final IdempotencyRecordMapper mapper;

    /** Overridden by tests. */
    Clock clock = Clock.systemDefaultZone();

    /** What the filter should do with a request that carries a key. */
    public sealed interface Claim {
        /** This request owns the key: run it, then {@link #complete} or {@link #release}. */
        record Acquired(UUID recordId) implements Claim {
        }

        /** The same request already completed: send this response back verbatim. */
        record Replay(int status, String contentType, byte[] body) implements Claim {
        }

        /** The same request is still running. */
        record InProgress() implements Claim {
        }

        /** The key was used for a different request. */
        record Mismatch() implements Claim {
        }
    }

    public Claim claim(UUID userId, String key, String method, String path, String fingerprint) {
        // Two rounds: the second follows removing an expired row that still held the key.
        for (int attempt = 0; attempt < 2; attempt++) {
            LocalDateTime now = LocalDateTime.now(clock);
            UUID id = UUID.randomUUID();
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .id(id)
                    .userId(userId)
                    .idempotencyKey(key)
                    .requestMethod(method)
                    .requestPath(truncate(path, 2048))
                    .requestFingerprint(fingerprint)
                    .state(IdempotencyRecord.IN_PROGRESS)
                    .lockedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .expiresAt(now.plus(TTL))
                    .build();
            if (mapper.insertIfAbsent(record) == 1) {
                return new Claim.Acquired(id);
            }

            IdempotencyRecord existing = mapper.findByUserAndKey(userId, key);
            if (existing == null) {
                // Released between our insert and our read; claim it on the next round.
                continue;
            }
            if (existing.getExpiresAt() != null && !existing.getExpiresAt().isAfter(now)) {
                mapper.deleteIfExpired(existing.getId(), now);
                continue;
            }
            if (!fingerprint.equals(existing.getRequestFingerprint())) {
                return new Claim.Mismatch();
            }
            if (IdempotencyRecord.COMPLETED.equals(existing.getState())) {
                return new Claim.Replay(existing.getResponseStatus(), existing.getResponseContentType(),
                        existing.getResponseBody() == null ? new byte[0] : existing.getResponseBody());
            }
            if (existing.getLockedAt() != null
                    && mapper.takeOverStale(existing.getId(), now.minus(STALE_LOCK), now) == 1) {
                log.warn("Took over an abandoned idempotency claim {} for user {}", existing.getId(), userId);
                return new Claim.Acquired(existing.getId());
            }
            return new Claim.InProgress();
        }
        // The key kept changing hands under us; the safe answer is "someone is on it".
        return new Claim.InProgress();
    }

    /**
     * Stores the response for replay when it is a final answer (2xx, or a 4xx other than the
     * transient 408, 425 and 429) small enough to keep; otherwise releases the key so the
     * client can retry.
     */
    public void complete(UUID recordId, int status, String contentType, byte[] body) {
        byte[] stored = body == null ? new byte[0] : body;
        if (!isReplayable(status) || stored.length > MAX_STORED_BODY_BYTES) {
            release(recordId);
            return;
        }
        mapper.complete(recordId, status, contentType, stored, LocalDateTime.now(clock));
    }

    /** Forgets the claim: the request failed in a way the client should be free to retry. */
    public void release(UUID recordId) {
        mapper.deleteById(recordId);
    }

    /** @return the number of expired rows deleted */
    public int purgeExpired() {
        LocalDateTime now = LocalDateTime.now(clock);
        int total = 0;
        for (int batch = 0; batch < PURGE_MAX_BATCHES; batch++) {
            int deleted = mapper.deleteExpired(now, PURGE_BATCH_SIZE);
            total += deleted;
            if (deleted < PURGE_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    static boolean isReplayable(int status) {
        if (status >= 200 && status < 300) {
            return true;
        }
        return status >= 400 && status < 500 && status != 408 && status != 425 && status != 429;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

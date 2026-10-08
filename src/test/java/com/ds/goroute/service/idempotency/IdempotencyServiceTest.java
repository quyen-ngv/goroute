package com.ds.goroute.service.idempotency;

import com.ds.goroute.entity.IdempotencyRecord;
import com.ds.goroute.mapper.IdempotencyRecordMapper;
import com.ds.goroute.service.idempotency.IdempotencyService.Claim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Idempotency-Key state machine against an in-memory table that honours the same unique
 * key and conditional updates the SQL does.
 */
class IdempotencyServiceTest {

    private static final String FINGERPRINT = "a".repeat(64);
    private static final String OTHER_FINGERPRINT = "b".repeat(64);

    private final InMemoryMapper table = new InMemoryMapper();
    private final IdempotencyService service = new IdempotencyService(table);
    private final UUID user = UUID.randomUUID();
    private Instant now = Instant.parse("2026-10-07T03:00:00Z");

    @BeforeEach
    void fixClock() {
        service.clock = Clock.fixed(now, ZoneOffset.UTC);
    }

    private void advance(Duration duration) {
        now = now.plus(duration);
        service.clock = Clock.fixed(now, ZoneOffset.UTC);
    }

    private Claim claim(String key, String fingerprint) {
        return service.claim(user, key, "POST", "/v1/api/trips", fingerprint);
    }

    @Test
    void firstRequestAcquiresTheKeyInProgress() {
        Claim claim = claim("k1", FINGERPRINT);

        assertThat(claim).isInstanceOf(Claim.Acquired.class);
        IdempotencyRecord row = table.rows.get(((Claim.Acquired) claim).recordId());
        assertThat(row.getState()).isEqualTo(IdempotencyRecord.IN_PROGRESS);
        assertThat(row.getExpiresAt()).isEqualTo(LocalDateTime.now(service.clock).plusDays(30));
    }

    @Test
    void repeatAfterCompletionReplaysTheStoredResponseVerbatim() {
        UUID id = ((Claim.Acquired) claim("k1", FINGERPRINT)).recordId();
        byte[] body = "{\"meta\":{\"code\":200000},\"data\":{\"id\":\"t1\"}}".getBytes(StandardCharsets.UTF_8);
        service.complete(id, 201, "application/json", body);

        Claim repeat = claim("k1", FINGERPRINT);

        assertThat(repeat).isInstanceOfSatisfying(Claim.Replay.class, replay -> {
            assertThat(replay.status()).isEqualTo(201);
            assertThat(replay.contentType()).isEqualTo("application/json");
            assertThat(replay.body()).isEqualTo(body);
        });
    }

    @Test
    void businessFourHundredsAreStoredAndReplayedToo() {
        UUID id = ((Claim.Acquired) claim("k1", FINGERPRINT)).recordId();
        service.complete(id, 400, "application/json", "{}".getBytes(StandardCharsets.UTF_8));

        assertThat(claim("k1", FINGERPRINT)).isInstanceOfSatisfying(Claim.Replay.class,
                replay -> assertThat(replay.status()).isEqualTo(400));
    }

    @Test
    void repeatWhileTheFirstIsRunningIsInProgress() {
        claim("k1", FINGERPRINT);

        assertThat(claim("k1", FINGERPRINT)).isInstanceOf(Claim.InProgress.class);
    }

    @Test
    void sameKeyOnADifferentRequestIsAMismatchWhetherRunningOrDone() {
        UUID id = ((Claim.Acquired) claim("k1", FINGERPRINT)).recordId();
        assertThat(claim("k1", OTHER_FINGERPRINT)).isInstanceOf(Claim.Mismatch.class);

        service.complete(id, 200, "application/json", new byte[0]);
        assertThat(claim("k1", OTHER_FINGERPRINT)).isInstanceOf(Claim.Mismatch.class);
    }

    @Test
    void serverErrorReleasesTheKeySoTheRetryRunsAgain() {
        UUID id = ((Claim.Acquired) claim("k1", FINGERPRINT)).recordId();

        service.complete(id, 503, "application/json", "{}".getBytes(StandardCharsets.UTF_8));

        assertThat(table.rows).isEmpty();
        assertThat(claim("k1", FINGERPRINT)).isInstanceOf(Claim.Acquired.class);
    }

    @Test
    void transientClientErrorsAndOversizedBodiesAreNotKept() {
        UUID rateLimited = ((Claim.Acquired) claim("k1", FINGERPRINT)).recordId();
        service.complete(rateLimited, 429, "application/json", new byte[0]);
        UUID huge = ((Claim.Acquired) claim("k2", FINGERPRINT)).recordId();
        service.complete(huge, 200, "application/json", new byte[IdempotencyService.MAX_STORED_BODY_BYTES + 1]);

        assertThat(table.rows).isEmpty();
    }

    @Test
    void keysAreScopedPerUser() {
        claim("k1", FINGERPRINT);

        Claim otherUser = service.claim(UUID.randomUUID(), "k1", "POST", "/v1/api/trips", OTHER_FINGERPRINT);

        assertThat(otherUser).isInstanceOf(Claim.Acquired.class);
    }

    @Test
    void anOfflineResendDaysLaterStillReplaysInsteadOfRunningAgain() {
        UUID first = ((Claim.Acquired) claim("k1", FINGERPRINT)).recordId();
        service.complete(first, 201, "application/json", new byte[0]);

        advance(Duration.ofDays(29));

        assertThat(claim("k1", FINGERPRINT)).isInstanceOf(Claim.Replay.class);
    }

    @Test
    void anExpiredRecordNoLongerHoldsTheKey() {
        UUID first = ((Claim.Acquired) claim("k1", FINGERPRINT)).recordId();
        service.complete(first, 200, "application/json", new byte[0]);

        advance(IdempotencyService.TTL.plusSeconds(1));

        assertThat(claim("k1", OTHER_FINGERPRINT)).isInstanceOfSatisfying(Claim.Acquired.class,
                acquired -> assertThat(acquired.recordId()).isNotEqualTo(first));
    }

    @Test
    void anAbandonedClaimIsTakenOverAfterTheStaleLockWindow() {
        UUID first = ((Claim.Acquired) claim("k1", FINGERPRINT)).recordId();

        advance(Duration.ofMinutes(4));
        assertThat(claim("k1", FINGERPRINT)).isInstanceOf(Claim.InProgress.class);

        advance(Duration.ofMinutes(2));
        assertThat(claim("k1", FINGERPRINT)).isInstanceOfSatisfying(Claim.Acquired.class,
                acquired -> assertThat(acquired.recordId()).isEqualTo(first));
    }

    @Test
    void purgeDeletesOnlyExpiredRows() {
        claim("old", FINGERPRINT);
        advance(IdempotencyService.TTL.minusHours(1));
        claim("fresh", FINGERPRINT);
        advance(Duration.ofHours(2));

        assertThat(service.purgeExpired()).isEqualTo(1);
        assertThat(table.rows.values()).extracting(IdempotencyRecord::getIdempotencyKey).containsExactly("fresh");
    }

    /** Mirrors the SQL: unique (user_id, key), conditional take-over and completion. */
    private static final class InMemoryMapper implements IdempotencyRecordMapper {
        final Map<UUID, IdempotencyRecord> rows = new HashMap<>();

        @Override
        public int insertIfAbsent(IdempotencyRecord record) {
            if (findByUserAndKey(record.getUserId(), record.getIdempotencyKey()) != null) {
                return 0;
            }
            rows.put(record.getId(), record);
            return 1;
        }

        @Override
        public IdempotencyRecord findByUserAndKey(UUID userId, String idempotencyKey) {
            return rows.values().stream()
                    .filter(row -> row.getUserId().equals(userId) && row.getIdempotencyKey().equals(idempotencyKey))
                    .findFirst().orElse(null);
        }

        @Override
        public int takeOverStale(UUID id, LocalDateTime staleBefore, LocalDateTime now) {
            IdempotencyRecord row = rows.get(id);
            if (row == null || !IdempotencyRecord.IN_PROGRESS.equals(row.getState())
                    || !row.getLockedAt().isBefore(staleBefore)) {
                return 0;
            }
            row.setLockedAt(now);
            return 1;
        }

        @Override
        public int complete(UUID id, int responseStatus, String responseContentType, byte[] responseBody,
                            LocalDateTime now) {
            IdempotencyRecord row = rows.get(id);
            if (row == null || !IdempotencyRecord.IN_PROGRESS.equals(row.getState())) {
                return 0;
            }
            row.setState(IdempotencyRecord.COMPLETED);
            row.setResponseStatus(responseStatus);
            row.setResponseContentType(responseContentType);
            row.setResponseBody(responseBody);
            return 1;
        }

        @Override
        public int deleteById(UUID id) {
            return rows.remove(id) == null ? 0 : 1;
        }

        @Override
        public int deleteIfExpired(UUID id, LocalDateTime now) {
            IdempotencyRecord row = rows.get(id);
            if (row == null || row.getExpiresAt().isAfter(now)) {
                return 0;
            }
            rows.remove(id);
            return 1;
        }

        @Override
        public int deleteExpired(LocalDateTime now, int limit) {
            List<UUID> expired = new ArrayList<>();
            rows.forEach((id, row) -> {
                if (!row.getExpiresAt().isAfter(now) && expired.size() < limit) {
                    expired.add(id);
                }
            });
            expired.forEach(rows::remove);
            return expired.size();
        }
    }
}

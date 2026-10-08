package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The counts that ride on feed and profile payloads, and the Idempotency-Key table. Each one
 * must answer in one statement and apply exactly the visibility rules of the list or endpoint
 * it stands in for.
 */
class SocialCountsMapperXmlTest {

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        for (String resource : new String[] {
                "mapper/ContentCommentMapper.xml", "mapper/UserCheckinMapper.xml", "mapper/IdempotencyRecordMapper.xml"}) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void commentCountsForAPageAreOneGroupedQueryWithTheSingleCountPredicate() {
        Map<String, Object> params = new HashMap<>();
        params.put("contentType", "CHECKIN");
        params.put("contentIds", List.of(UUID.randomUUID(), UUID.randomUUID()));
        params.put("excludeTakenDownTargets", true);

        BoundSql bound = bound("com.ds.goroute.mapper.ContentCommentMapper.countActiveByTargets", params);
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .startsWith("SELECT c.content_id AS \"contentId\", COUNT(*) AS \"commentCount\" FROM content_comments c")
                .contains("WHERE c.content_type = ? AND c.content_id IN ( ? , ? )")
                .contains("AND c.is_deleted IS DISTINCT FROM TRUE")
                .contains("td.content_type = 'CONTENT_COMMENT' AND td.content_id = c.id AND td.restored_at IS NULL")
                .contains("target.content_type = ? AND target.content_id = c.content_id AND target.restored_at IS NULL")
                .endsWith("GROUP BY c.content_id")
                .doesNotContain("SELECT *");
    }

    @Test
    void reviewCountsKeepTheThreadOfATakenDownReview() {
        Map<String, Object> params = new HashMap<>();
        params.put("contentType", "REVIEW");
        params.put("contentIds", List.of(UUID.randomUUID()));
        params.put("excludeTakenDownTargets", false);

        String sql = collapse(bound("com.ds.goroute.mapper.ContentCommentMapper.countActiveByTargets", params).getSql());

        assertThat(sql).doesNotContain("target.content_type");
    }

    @Test
    void profileCheckinCountMatchesTheByUserList() {
        UUID userId = UUID.randomUUID();
        String othersCount = collapse(bound("com.ds.goroute.mapper.UserCheckinMapper.countByUser",
                Map.of("userId", userId, "includePrivate", false)).getSql());
        String ownCount = collapse(bound("com.ds.goroute.mapper.UserCheckinMapper.countByUser",
                Map.of("userId", userId, "includePrivate", true)).getSql());
        String othersList = collapse(bound("com.ds.goroute.mapper.UserCheckinMapper.findByUser",
                Map.of("userId", userId, "includePrivate", false, "limit", 20, "offset", 0)).getSql());

        assertThat(othersCount)
                .isEqualTo("SELECT COUNT(*) FROM user_checkins WHERE user_id = ? AND is_removed = FALSE "
                        + "AND visibility = 'PUBLIC' AND NOT EXISTS ( SELECT 1 FROM content_takedowns t "
                        + "WHERE t.content_type = 'CHECKIN' AND t.content_id = user_checkins.id "
                        + "AND t.restored_at IS NULL)");
        assertThat(othersList).contains(othersCount.substring(othersCount.indexOf("WHERE")));
        assertThat(ownCount)
                .isEqualTo("SELECT COUNT(*) FROM user_checkins WHERE user_id = ? AND is_removed = FALSE");
    }

    @Test
    void idempotencyClaimIsAnInsertThatYieldsToAnExistingKey() {
        BoundSql bound = configuration.getMappedStatement("com.ds.goroute.mapper.IdempotencyRecordMapper.insertIfAbsent")
                .getBoundSql(new com.ds.goroute.entity.IdempotencyRecord());
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .startsWith("INSERT INTO idempotency_keys")
                .endsWith("ON CONFLICT (user_id, idempotency_key) DO NOTHING");
        assertThat(bound.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .contains("userId", "idempotencyKey", "requestFingerprint", "expiresAt");
    }

    @Test
    void idempotencyCompletionAndTakeOverOnlyTouchInProgressRows() {
        Map<String, Object> params = new HashMap<>();
        params.put("id", UUID.randomUUID());
        params.put("responseStatus", 201);
        params.put("responseContentType", "application/json");
        params.put("responseBody", new byte[0]);
        params.put("now", LocalDateTime.now());
        params.put("staleBefore", LocalDateTime.now());

        assertThat(collapse(bound("com.ds.goroute.mapper.IdempotencyRecordMapper.complete", params).getSql()))
                .endsWith("WHERE id = ? AND state = 'IN_PROGRESS'");
        assertThat(collapse(bound("com.ds.goroute.mapper.IdempotencyRecordMapper.takeOverStale", params).getSql()))
                .endsWith("WHERE id = ? AND state = 'IN_PROGRESS' AND locked_at < ?");
        assertThat(collapse(bound("com.ds.goroute.mapper.IdempotencyRecordMapper.deleteIfExpired", params).getSql()))
                .isEqualTo("DELETE FROM idempotency_keys WHERE id = ? AND expires_at <= ?");
    }

    @Test
    void idempotencyPurgeIsBatched() {
        String sql = collapse(bound("com.ds.goroute.mapper.IdempotencyRecordMapper.deleteExpired",
                Map.of("now", LocalDateTime.now(), "limit", 1000)).getSql());

        assertThat(sql).isEqualTo("DELETE FROM idempotency_keys WHERE id IN ( SELECT id FROM idempotency_keys "
                + "WHERE expires_at <= ? ORDER BY expires_at LIMIT ? )");
    }

    private static BoundSql bound(String statementId, Map<String, Object> params) {
        return configuration.getMappedStatement(statementId).getBoundSql(params);
    }

    private static String collapse(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}

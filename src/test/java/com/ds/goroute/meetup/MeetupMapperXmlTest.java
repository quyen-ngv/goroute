package com.ds.goroute.meetup;

import com.ds.goroute.config.database.UUIDTypeHandler;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.persistence.MeetupAdminQuery;
import com.ds.goroute.meetup.persistence.MeetupDiscoveryQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The meetup SQL compiled without a database. The discovery query is the one that decides who
 * sees a stranger's meetup, so every filter that keeps one hidden is pinned here: approved only,
 * window still open, not taken down, no block in either direction, inside the box and the circle.
 */
@DisplayName("MeetupMapper.xml")
class MeetupMapperXmlTest {

    private static final String NS = "com.ds.goroute.meetup.persistence.MeetupMapper.";
    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        String resource = "mapper/MeetupMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static MeetupDiscoveryQuery discovery(List<String> categories) {
        return MeetupDiscoveryQuery.builder()
                .viewerId(UUID.randomUUID())
                .latitude(new BigDecimal("21.05"))
                .longitude(new BigDecimal("105.82"))
                .radiusKm(30)
                .minLat(new BigDecimal("20.78"))
                .maxLat(new BigDecimal("21.32"))
                .minLng(new BigDecimal("105.53"))
                .maxLng(new BigDecimal("106.11"))
                .categories(categories)
                .now(LocalDateTime.of(2026, 10, 4, 3, 0))
                .limit(20)
                .offset(0)
                .build();
    }

    private static BoundSql bound(String id, Object parameter) {
        MappedStatement statement = configuration.getMappedStatement(NS + id);
        return statement.getBoundSql(parameter);
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String collapse(String sql) {
        return sql.replaceAll("\\s+", " ").replace("( ", "(").replace(" )", ")").trim();
    }

    @Test
    void discoveryShowsOnlyLiveApprovedMeetupsNearbyWithoutTakedownsOrBlocks() {
        BoundSql bound = bound("findDiscoverable", params("p", discovery(List.of("COFFEE", "SOCIAL"))));
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .contains("FROM meetups m WHERE m.status = 'APPROVED'")
                .contains("AND m.visible_until > ?")
                .contains("AND m.display_latitude BETWEEN ? AND ?")
                .contains("AND m.display_longitude BETWEEN ? AND ?")
                .contains("6371 * acos(")
                .contains("<= ?")
                .contains("AND m.category IN (? , ?)")
                .contains("NOT EXISTS (SELECT 1 FROM content_takedowns t WHERE t.content_type = 'MEETUP' "
                        + "AND t.content_id = m.id AND t.restored_at IS NULL)")
                .contains("NOT EXISTS (SELECT 1 FROM user_blocks b "
                        + "WHERE (b.blocker_id = ? AND b.blocked_id = m.host_user_id) "
                        + "OR (b.blocker_id = m.host_user_id AND b.blocked_id = ?))")
                .doesNotContain("SELECT *")
                .doesNotContain("m.latitude BETWEEN")
                .endsWith("ORDER BY COALESCE(m.starts_at, CAST(m.meetup_date AS TIMESTAMP)) ASC, "
                        + "m.created_at ASC, m.id ASC LIMIT ? OFFSET ?");
        assertThat(bound.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("p.now", "p.minLat", "p.maxLat", "p.minLng", "p.maxLng",
                        "p.latitude", "p.longitude", "p.latitude", "p.radiusKm",
                        "__frch_category_0", "__frch_category_1",
                        "p.viewerId", "p.viewerId", "p.limit", "p.offset");
    }

    @Test
    void discoveryWithoutCategoriesHasNoCategoryFilterAndTheCountSharesTheFilters() {
        String find = collapse(bound("findDiscoverable", params("p", discovery(List.of()))).getSql());
        String count = collapse(bound("countDiscoverable", params("p", discovery(List.of()))).getSql());

        assertThat(find).doesNotContain("m.category IN");
        assertThat(count).startsWith("SELECT COUNT(*) FROM meetups m WHERE m.status = 'APPROVED'")
                .contains("content_takedowns").contains("user_blocks").doesNotContain("LIMIT");
    }

    @Test
    void theConsoleListOrdersByAWhitelistedSortAndIsBounded() {
        MeetupAdminQuery query = MeetupAdminQuery.builder()
                .statuses(List.of("PENDING_REVIEW"))
                .categories(List.of())
                .search("chạy")
                .hidePast(true)
                .now(LocalDateTime.of(2026, 10, 4, 3, 0))
                .sort("DEADLINE")
                .limit(50)
                .offset(0)
                .build();
        String deadline = collapse(bound("findAdmin", params("p", query)).getSql());
        assertThat(deadline)
                .contains("m.status IN (?)")
                .contains("m.title ILIKE CONCAT('%', ?, '%')")
                .contains("m.visible_until > ?")
                .doesNotContain("m.category IN")
                .endsWith("ORDER BY m.visible_until ASC, m.id ASC LIMIT ? OFFSET ?");

        query.setSort("SUBMITTED_AT");
        query.setSearch(null);
        query.setHidePast(false);
        String submitted = collapse(bound("findAdmin", params("p", query)).getSql());
        assertThat(submitted)
                .doesNotContain("ILIKE")
                .doesNotContain("visible_until >")
                .endsWith("ORDER BY m.submitted_at DESC, m.id DESC LIMIT ? OFFSET ?");
    }

    @Test
    void statusChangesAreGuardedByStatusOwnerWindowAndVersion() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 10, 4, 3, 0);

        assertThat(collapse(bound("updateReview", params("id", id, "expectedVersion", 4L, "status", "APPROVED",
                "rejectReason", null, "reviewNote", null, "reviewedBy", id, "now", now)).getSql()))
                .endsWith("WHERE id = ? AND status = 'PENDING_REVIEW' AND visible_until > ? AND data_version = ?");
        assertThat(collapse(bound("updateContent", params("m", Meetup.builder().id(id).build(),
                "expectedVersion", 1L)).getSql()))
                .endsWith("WHERE id = ? AND host_user_id = ? AND status IN ('PENDING_REVIEW', 'REJECTED') "
                        + "AND data_version = ?");
        assertThat(collapse(bound("updateWithdrawn", params("id", id, "hostUserId", id, "now", now)).getSql()))
                .endsWith("WHERE id = ? AND host_user_id = ? AND status = 'PENDING_REVIEW'");
        assertThat(collapse(bound("updateCancelled", params("id", id, "hostUserId", id, "reason", null,
                "now", now)).getSql()))
                .endsWith("WHERE id = ? AND host_user_id = ? AND status = 'APPROVED' AND visible_until > ?");
        assertThat(collapse(bound("updateRemoved", params("id", id, "reason", "x", "reviewedBy", id,
                "now", now)).getSql()))
                .endsWith("WHERE id = ? AND status = 'APPROVED'");
        assertThat(collapse(bound("updateParticipantStatus", params("meetupId", id, "userId", id,
                "fromStatus", "REQUESTED", "toStatus", "JOINED", "now", now)).getSql()))
                .endsWith("WHERE meetup_id = ? AND user_id = ? AND status = ?");
    }

    @Test
    void openRequestsCountOnlyLiveApprovedMeetupsAndActiveHostingSkipsPastOnes() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 4, 3, 0);
        assertThat(collapse(bound("countOpenRequests", params("userId", UUID.randomUUID(), "now", now)).getSql()))
                .contains("p.status = 'REQUESTED' AND m.status = 'APPROVED' AND m.visible_until > ?");
        assertThat(collapse(bound("countActiveByHost", params("hostUserId", UUID.randomUUID(), "now", now,
                "excludeId", UUID.randomUUID())).getSql()))
                .contains("m.status IN ('PENDING_REVIEW', 'APPROVED') AND m.visible_until > ?")
                .endsWith("AND m.id <> ?");
    }

    @Test
    void participationsHideTakenDownMeetupsAndAreBounded() {
        String sql = collapse(bound("findParticipations", params("userId", UUID.randomUUID(), "limit", 20,
                "offset", 0)).getSql());

        assertThat(sql)
                .contains("p.status IN ('REQUESTED', 'INVITED', 'JOINED')")
                .contains("m.status IN ('APPROVED', 'CANCELLED')")
                .contains("content_takedowns")
                .endsWith("ORDER BY p.requested_at DESC, m.id DESC LIMIT ? OFFSET ?");
    }

    @Test
    void noStatementSelectsStarOrSplicesText() throws Exception {
        String xml;
        try (InputStream input = Resources.getResourceAsStream("mapper/MeetupMapper.xml")) {
            xml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(xml).doesNotContain("SELECT *").doesNotContain("${");
    }
}

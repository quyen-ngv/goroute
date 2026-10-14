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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two queries behind the Home "Friends" tab.
 *
 * <p>Following someone is not consent to be shown to them: blocking does not remove a
 * follow, so each query has to drop blocks in both directions on its own. These tests pin
 * every filter that keeps a post or trip private, so dropping one fails the build rather
 * than leaking content.
 */
class FollowingFeedMapperXmlTest {

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        for (String resource : new String[] {"mapper/UserCheckinMapper.xml", "mapper/TripMapper.xml"}) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void followingCheckinsArePublicPostsByLiveFollowedAuthorsWithoutBlocksOrTakedowns() {
        BoundSql bound = bound("com.ds.goroute.mapper.UserCheckinMapper.findFollowingFeed");
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .contains("WHERE user_id IN ( SELECT f.following_id FROM user_follows f "
                        + "JOIN users u ON u.id = f.following_id AND u.deleted_at IS NULL "
                        + "WHERE f.follower_id = ?)")
                .contains("AND visibility = 'PUBLIC'")
                .contains("AND is_removed = FALSE")
                .contains("NOT EXISTS ( SELECT 1 FROM user_blocks b "
                        + "WHERE (b.blocker_id = ? AND b.blocked_id = user_checkins.user_id) "
                        + "OR (b.blocker_id = user_checkins.user_id AND b.blocked_id = ?))")
                .contains("NOT EXISTS ( SELECT 1 FROM content_takedowns t "
                        + "WHERE t.content_type = 'CHECKIN' AND t.content_id = user_checkins.id "
                        + "AND t.restored_at IS NULL)")
                .doesNotContain("SELECT *")
                // Newest first: a map of where friends have been is read by recency, not by
                // the public feed's photos-first ranking.
                .doesNotContain("media_assets")
                .contains("NULL::int AS focus_rank")
                .endsWith("ORDER BY focus_rank ASC NULLS LAST, created_at DESC, id DESC LIMIT ? OFFSET ?");
        assertThat(bound.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("viewerId", "viewerId", "viewerId", "limit", "offset");
    }

    @Test
    void aFocusPointRanksCheckinsByItsProvinceThenByRing() {
        BoundSql bound = focused("com.ds.goroute.mapper.UserCheckinMapper.findFollowingFeed");
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .contains("ST_Covers(fp.geom, ST_SetSRID(ST_MakePoint(?::float8, ?::float8), 4326))")
                .contains("(SELECT w.province_code FROM wards w WHERE w.code = user_checkins.ward_code)")
                .contains("THEN 0")
                .contains("ELSE 1 + FLOOR(ST_Distance(")
                .contains("/ 100000)::int END AS focus_rank")
                // The privacy filters do not depend on the focus.
                .contains("AND visibility = 'PUBLIC'")
                .endsWith("ORDER BY focus_rank ASC NULLS LAST, created_at DESC, id DESC LIMIT ? OFFSET ?");
        assertThat(bound.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("focusLng", "focusLat", "focusLat", "focusLng",
                        "viewerId", "viewerId", "viewerId", "limit", "offset");
    }

    @Test
    void aFocusPointRanksTripsByTheirDestinationLikeCheckins() {
        BoundSql bound = focused("com.ds.goroute.mapper.TripMapper.findFollowingTrips");
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .contains("WHEN t.destination_lat IS NULL OR t.destination_lng IS NULL THEN NULL")
                .contains("ST_Covers(fp.geom, ST_SetSRID(ST_MakePoint(t.destination_lng::float8, "
                        + "t.destination_lat::float8), 4326))) THEN 0")
                .contains("/ 100000)::int END AS focus_rank")
                .endsWith("ORDER BY focus_rank ASC NULLS LAST, COALESCE(t.public_shared_at, t.created_at) DESC, "
                        + "t.id DESC LIMIT ? OFFSET ?");
        assertThat(bound.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("focusLng", "focusLat", "focusLat", "focusLng",
                        "viewerId", "viewerId", "viewerId", "limit", "offset");
    }

    @Test
    void followingTripsArePublicTripsOfLiveFollowedOwnersWithoutBlocksOrTakedowns() {
        BoundSql bound = bound("com.ds.goroute.mapper.TripMapper.findFollowingTrips");
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .contains("FROM trips t JOIN user_follows f ON f.following_id = t.owner_id "
                        + "AND f.follower_id = ? "
                        + "JOIN users u ON u.id = t.owner_id AND u.deleted_at IS NULL")
                .contains("WHERE t.visibility = 'PUBLIC' AND t.is_deleted = FALSE")
                .contains("NOT EXISTS ( SELECT 1 FROM user_blocks b "
                        + "WHERE (b.blocker_id = ? AND b.blocked_id = t.owner_id) "
                        + "OR (b.blocker_id = t.owner_id AND b.blocked_id = ?))")
                .contains("NOT EXISTS ( SELECT 1 FROM content_takedowns ct "
                        + "WHERE ct.content_type = 'TRIP' AND ct.content_id = t.id "
                        + "AND ct.restored_at IS NULL)")
                .contains("t.location_image_id, NULL::int AS focus_rank FROM trips t")
                .doesNotContain("SELECT *")
                .contains("NULL::int AS focus_rank")
                .endsWith("ORDER BY focus_rank ASC NULLS LAST, COALESCE(t.public_shared_at, t.created_at) DESC, "
                        + "t.id DESC LIMIT ? OFFSET ?");
        assertThat(bound.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("viewerId", "viewerId", "viewerId", "limit", "offset");
    }

    private static BoundSql bound(String statementId) {
        return configuration.getMappedStatement(statementId)
                .getBoundSql(Map.of("viewerId", UUID.randomUUID(), "limit", 20, "offset", 40));
    }

    private static BoundSql focused(String statementId) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("viewerId", UUID.randomUUID());
        params.put("focusLat", 21.0285);
        params.put("focusLng", 105.8542);
        params.put("limit", 20);
        params.put("offset", 40);
        return configuration.getMappedStatement(statementId).getBoundSql(params);
    }

    private static String collapse(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}

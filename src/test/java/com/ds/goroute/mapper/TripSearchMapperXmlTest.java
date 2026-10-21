package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import com.ds.goroute.dto.PublicTripSearchCriteria;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Public trip search and the batch queries behind a page of trip cards.
 *
 * <p>Pins what keeps the search correct (takedowns, visibility, the viewer's exclusion,
 * a unique last sort key) and fast (the radius test spelled like the GiST index
 * expressions of V214, ranking from the V216 columns instead of a count per row).
 */
class TripSearchMapperXmlTest {

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        for (String resource : new String[] {
                "mapper/TripMapper.xml", "mapper/MediaAssetMapper.xml", "mapper/ActivityMapper.xml",
                "mapper/TripMemberMapper.xml", "mapper/TripDestinationMapper.xml",
                "mapper/TripHelpfulVoteMapper.xml"}) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void nearestFirstSearchUsesTheGistIndexesAndOrdersByDistanceThenId() {
        PublicTripSearchCriteria criteria = criteria(false, null, false, false);

        BoundSql bound = ranking(criteria, UUID.randomUUID());
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .contains("ST_DWithin(goroute_point(td.lat, td.lng), goroute_point(?, ?), ?, false)")
                .contains("ST_DWithin(goroute_point(tr.destination_lat, tr.destination_lng), goroute_point(?, ?), ?, false)")
                .contains("td.lat IS NOT NULL AND td.lng IS NOT NULL")
                .contains("ST_Distance(goroute_point(td.lat, td.lng), goroute_point(?, ?), false)")
                .contains("NOT EXISTS ( SELECT 1 FROM content_takedowns ct WHERE ct.content_type = 'TRIP' "
                        + "AND ct.content_id = t.id AND ct.restored_at IS NULL)")
                .contains("WHERE t.visibility = 'PUBLIC' AND t.is_deleted = FALSE")
                .contains("AND t.owner_id != ?")
                .contains("tm.status = 'ACCEPTED'")
                .doesNotContain("SELECT *")
                .doesNotContain("acos(")
                .endsWith("ORDER BY nearest.distance_m ASC, COALESCE(t.public_shared_at, t.created_at) DESC, t.id "
                        + "LIMIT ? OFFSET ?");
        assertThat(bound.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .contains("criteria.latitude", "criteria.longitude", "criteria.radiusMeters",
                        "excludeUserId", "limit", "offset");
    }

    @Test
    void recommendedRankingReadsTheStoredCountsAndSkipsTheDistance() {
        PublicTripSearchCriteria criteria = criteria(false, null, true, true);

        String sql = collapse(ranking(criteria, null).getSql());

        assertThat(sql)
                .contains("JOIN ( SELECT td.trip_id FROM trip_destinations td")
                .contains(" UNION SELECT tr.id AS trip_id FROM trips tr")
                .doesNotContain("ST_Distance")
                .doesNotContain("COUNT(*)")
                .doesNotContain("media_assets")
                .doesNotContain("t.owner_id != ?")
                .contains("(NULLIF(TRIM(COALESCE(t.cover_image_url, '')), '') IS NOT NULL OR t.memory_count > 0) DESC,")
                .contains("LEAST(t.activity_count, 12) * 3 + LEAST(t.memory_count, 10) * 2")
                .endsWith("COALESCE(t.public_shared_at, t.created_at) DESC, t.id LIMIT ? OFFSET ?");
    }

    @Test
    void allPublicSearchNeverTouchesCoordinatesAndEndsOnTheId() {
        PublicTripSearchCriteria criteria = criteria(true, null, false, false);

        BoundSql bound = ranking(criteria, null);
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .doesNotContain("trip_destinations td")
                .doesNotContain("goroute_point")
                .contains("content_takedowns")
                .endsWith("ORDER BY COALESCE(t.public_shared_at, t.created_at) DESC, t.id DESC LIMIT ? OFFSET ?");
        assertThat(bound.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("limit", "offset");
    }

    @Test
    void seededOrderBreaksTiesOnTheId() {
        PublicTripSearchCriteria criteria = criteria(true, "seed-1", false, false);

        assertThat(collapse(ranking(criteria, null).getSql()))
                .endsWith("ORDER BY MD5(t.id::text || ?), t.id LIMIT ? OFFSET ?");
    }

    @Test
    void photosOnlyKeepsTripsWithMemoryPhotos() {
        PublicTripSearchCriteria criteria = criteria(true, null, true, true).withPhotoRules(true, false);

        assertThat(collapse(ranking(criteria, null).getSql()))
                .contains("AND t.memory_count > 0 ORDER BY");
    }

    @Test
    void photosThenStopsRanksPhotosThenStopsThenNewest() {
        // Near, so without the rule this would be the nearest-first order.
        PublicTripSearchCriteria criteria = criteria(false, null, false, false).withPhotoRules(false, true);

        String sql = collapse(ranking(criteria, null).getSql());

        assertThat(criteria.orderedByDistance()).isFalse();
        assertThat(sql)
                .doesNotContain("distance_m")
                .doesNotContain("t.memory_count > 0 ORDER BY")
                .endsWith("ORDER BY (t.memory_count > 0) DESC, t.activity_count DESC, "
                        + "COALESCE(t.public_shared_at, t.created_at) DESC, t.id LIMIT ? OFFSET ?");
    }

    @Test
    void textFiltersMatchTheTripOrAnyOfItsDestinations() {
        PublicTripSearchCriteria criteria = PublicTripSearchCriteria.of(
                null, null, null, " Đà Lạt ", "Hồ", true, null, false, false);

        String sql = collapse(ranking(criteria, null).getSql());

        assertThat(sql)
                .contains("LOWER(t.destination) LIKE LOWER(CONCAT('%', ?, '%')) OR EXISTS ( SELECT 1 FROM trip_destinations x")
                .contains("LOWER(t.description) LIKE LOWER(CONCAT('%', ?, '%'))");
    }

    @Test
    void criteriaRecordValuesResolveAsMyBatisParameters() {
        PublicTripSearchCriteria criteria = criteria(false, null, false, false);
        Map<String, Object> params = params(criteria, null);

        MetaObject meta = configuration.newMetaObject(params);

        assertThat(meta.getValue("criteria.latitude")).isEqualTo(21.03);
        assertThat(meta.getValue("criteria.radiusMeters")).isEqualTo(1_000_000d);
    }

    @Test
    void pageHydrationRechecksVisibilityAndTakedowns() {
        BoundSql bound = configuration.getMappedStatement("com.ds.goroute.mapper.TripMapper.selectPublicByIds")
                .getBoundSql(Map.of("ids", List.of(UUID.randomUUID(), UUID.randomUUID())));
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .contains("WHERE t.id IN ( ? , ? )")
                .contains("AND t.visibility = 'PUBLIC' AND t.is_deleted = FALSE")
                .contains("content_takedowns")
                .doesNotContain("SELECT *");
    }

    @Test
    void memoryPreviewsAreCappedPerTripInGalleryOrder() {
        BoundSql bound = configuration.getMappedStatement(
                        "com.ds.goroute.mapper.MediaAssetMapper.selectTripMemoryPreviewsByTripIds")
                .getBoundSql(Map.of("tripIds", List.of(UUID.randomUUID()), "perTrip", 6));
        String sql = collapse(bound.getSql());

        assertThat(sql)
                .contains("ROW_NUMBER() OVER ( PARTITION BY trip_id ORDER BY COALESCE(position, 32767), "
                        + "COALESCE(taken_at, created_at) DESC, id )")
                .contains("entity_type IN ('TRIP_MEMORY', 'TRIP_ACTIVITY_MEMORY')")
                .contains("deleted_at IS NULL")
                .contains("WHERE preview_rank <= ?")
                .doesNotContain("SELECT *");
    }

    @Test
    void cardCountsAreOneGroupedQueryEach() {
        Map<String, Object> tripIds = Map.of("tripIds", List.of(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(collapse(sql("com.ds.goroute.mapper.TripMemberMapper.countAcceptedByTripIds", tripIds)))
                .contains("WHERE status = 'ACCEPTED'")
                .endsWith("GROUP BY trip_id");
        assertThat(collapse(sql("com.ds.goroute.mapper.ActivityMapper.countByTripIds", tripIds)))
                .endsWith("GROUP BY trip_id");
        assertThat(collapse(sql("com.ds.goroute.mapper.MediaAssetMapper.countTripMemoriesByTripIds", tripIds)))
                .contains("(asset_role IS NULL OR asset_role = 'MEMORY')")
                .endsWith("GROUP BY trip_id");
    }

    @Test
    void counterRefreshesRecountFromTheSourceRows() {
        Map<String, Object> trip = Map.of("tripId", UUID.randomUUID());

        assertThat(collapse(sql("com.ds.goroute.mapper.TripMapper.refreshActivityCount", trip)))
                .isEqualTo("UPDATE trips SET activity_count = (SELECT COUNT(*) FROM activities a "
                        + "WHERE a.trip_id = trips.id) WHERE id = ?");
        assertThat(collapse(sql("com.ds.goroute.mapper.TripMapper.refreshMemoryCount", trip)))
                .contains("ma.entity_type IN ('TRIP_MEMORY', 'TRIP_ACTIVITY_MEMORY')")
                .endsWith("WHERE id = ?");
        assertThat(collapse(sql("com.ds.goroute.mapper.TripMapper.reconcileContentCounts", Map.of())))
                .endsWith("IS DISTINCT FROM (counted.activity_count, counted.memory_count)");
    }

    private static PublicTripSearchCriteria criteria(boolean allPublic, String seed,
                                                     boolean preferImages, boolean recommended) {
        return PublicTripSearchCriteria.of(BigDecimal.valueOf(21.0285), BigDecimal.valueOf(105.8542),
                BigDecimal.valueOf(1000), null, null, allPublic, seed, preferImages, recommended);
    }

    private static Map<String, Object> params(PublicTripSearchCriteria criteria, UUID excludeUserId) {
        Map<String, Object> params = new HashMap<>();
        params.put("criteria", criteria);
        params.put("excludeUserId", excludeUserId);
        params.put("limit", 20);
        params.put("offset", 0);
        return params;
    }

    private static BoundSql ranking(PublicTripSearchCriteria criteria, UUID excludeUserId) {
        return configuration.getMappedStatement("com.ds.goroute.mapper.TripMapper.searchPublicTripRanking")
                .getBoundSql(params(criteria, excludeUserId));
    }

    private static String sql(String statementId, Map<String, Object> params) {
        return configuration.getMappedStatement(statementId).getBoundSql(params).getSql();
    }

    private static String collapse(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}

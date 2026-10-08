package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The radius queries must spell goroute_point(...) exactly as idx_places_point (V214) does. */
class PlaceMapperXmlTest {

    private static final String INDEXED_POINT = "goroute_point(latitude, longitude)";

    private Configuration configuration;

    @BeforeEach
    void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        String resource = "mapper/PlaceMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void duplicateCandidatesUseTheGistIndexForRadiusAndOrder() {
        String sql = sql("findNearbyIdentityCandidates", Map.of(
                "latitude", BigDecimal.TEN, "longitude", BigDecimal.ONE,
                "maxDistanceMeters", BigDecimal.valueOf(25), "limit", 5));

        assertThat(sql)
                .contains("ST_DWithin(" + INDEXED_POINT)
                .contains("ORDER BY " + INDEXED_POINT + " <-> goroute_point(")
                .contains("LIMIT ?")
                .doesNotContain("*")
                .doesNotContain("acos(");
    }

    @Test
    void aiRadiusQueriesNoLongerComputeHaversinePerRow() {
        Map<String, Object> radius = new HashMap<>();
        radius.put("latitude", BigDecimal.TEN);
        radius.put("longitude", BigDecimal.ONE);
        radius.put("radiusKm", BigDecimal.TEN);
        radius.put("placeGroups", List.of("FOOD_AND_DRINK"));
        radius.put("limit", 50);
        String withinRadius = sql("findActiveForAiWithinRadius", radius);
        assertThat(withinRadius).contains("ST_DWithin(goroute_point(p.latitude, p.longitude)").doesNotContain("acos(");

        Map<String, Object> destination = new HashMap<>();
        destination.put("citySlugJson", "[\"hanoi\"]");
        destination.put("latitude", BigDecimal.TEN);
        destination.put("longitude", BigDecimal.ONE);
        destination.put("placeGroup", "FOOD_AND_DRINK");
        destination.put("minRating", null);
        destination.put("limit", 50);
        assertThat(sql("findForAiByDestination", destination)).doesNotContain("acos(").endsWith("p.id LIMIT ?");
    }

    @Test
    void guestListIsActiveOnlyNewestFirstWithAnIdTieBreakerAndNoHeavyColumns() {
        Map<String, Object> params = new HashMap<>();
        params.put("placeGroups", null);
        params.put("publicOnly", true);
        params.put("limit", 20);
        params.put("offset", 0);
        String sql = sql("findListPage", params);

        assertThat(sql)
                .contains("visibility_status = 'ACTIVE' AND lifecycle_status = 'ACTIVE'")
                .contains("ORDER BY created_at DESC, id DESC")
                .doesNotContain("raw_data").doesNotContain("menu").doesNotContain("popular_times")
                .doesNotContain("about").doesNotContain("reviews_per_rating");

        params.put("publicOnly", false);
        assertThat(sql("findListPage", params)).doesNotContain("lifecycle_status");
    }

    @Test
    void reindexReadsByKeysetNotOffset() {
        Map<String, Object> params = new HashMap<>();
        params.put("afterId", UUID.randomUUID());
        params.put("limit", 500);
        assertThat(sql("findSearchIndexPage", params))
                .contains("id > ?")
                .contains("ORDER BY id LIMIT ?")
                .doesNotContain("OFFSET");
    }

    private String sql(String statement, Map<String, Object> params) {
        return configuration.getMappedStatement("com.ds.goroute.mapper.PlaceMapper." + statement)
                .getBoundSql(params).getSql().replaceAll("\\s+", " ").trim();
    }
}

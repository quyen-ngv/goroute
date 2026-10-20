package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.dto.PublicTripSearchCriteria;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Feed's place-kind chips on the five queries behind the Feed: the public and the
 * following check-ins, the public reviews, and the public and the following trips.
 *
 * <p>A post matches through its catalogue place and a trip through any of its stops, and
 * with no kind asked for none of the queries may change at all.
 */
class FeedPlaceKindMapperXmlTest {

    private static final String CHECKINS = "com.ds.goroute.mapper.UserCheckinMapper.";
    private static final String REVIEWS = "com.ds.goroute.mapper.UserReviewMapper.";
    private static final String TRIPS = "com.ds.goroute.mapper.TripMapper.";

    private static final FeedPlaceKind CAFES = FeedPlaceKind.of(null, List.of("cafe"));
    private static final FeedPlaceKind ATTRACTIONS = FeedPlaceKind.of(List.of("ATTRACTIONS"), null);

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        for (String resource : new String[] {
                "mapper/UserCheckinMapper.xml", "mapper/UserReviewMapper.xml", "mapper/TripMapper.xml"}) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void checkinsMatchThroughTheirCataloguePlace() {
        for (String statement : List.of("findFeed", "findFollowingFeed")) {
            BoundSql bound = bound(CHECKINS + statement, CAFES);

            assertThat(collapse(bound.getSql()))
                    .as(statement)
                    .contains("AND EXISTS ( SELECT 1 FROM places kp WHERE kp.id = user_checkins.place_id "
                            + "AND kp.sub_types && ARRAY[ ? ]::varchar[] )")
                    .doesNotContain("kp.place_group");
            assertThat(properties(bound)).as(statement).contains("kindSubType");
        }
    }

    @Test
    void reviewsMatchThroughTheReviewedPlace() {
        BoundSql bound = bound(REVIEWS + "findFeedReviews", ATTRACTIONS);

        assertThat(collapse(bound.getSql()))
                .contains("AND EXISTS ( SELECT 1 FROM places kp WHERE kp.id = r.place_id "
                        + "AND kp.place_group IN ( ? ) )")
                .doesNotContain("kp.sub_types");
    }

    @Test
    void tripsMatchWhenAnyStopIsOfTheKind() {
        Map<String, Object> following = params(CAFES);
        String followingSql = collapse(configuration.getMappedStatement(TRIPS + "findFollowingTrips")
                .getBoundSql(following).getSql());

        Map<String, Object> search = new HashMap<>();
        search.put("criteria", PublicTripSearchCriteria.of(null, null, null, null, null,
                true, null, true, true, CAFES));
        search.put("limit", 20);
        search.put("offset", 0);
        String rankingSql = collapse(configuration.getMappedStatement(TRIPS + "searchPublicTripRanking")
                .getBoundSql(search).getSql());

        for (String sql : List.of(followingSql, rankingSql)) {
            assertThat(sql)
                    // By the stop's place reference, or its Google id from before that column.
                    .contains("SELECT 1 FROM activities ka JOIN places kp ON kp.id = ka.place_ref_id "
                            + "WHERE ka.trip_id = t.id AND COALESCE(ka.status, '') <> 'REJECTED' "
                            + "AND kp.sub_types && ARRAY[ ? ]::varchar[] )")
                    .contains("JOIN places kp ON kp.place_id = ka.place_id "
                            + "WHERE ka.trip_id = t.id AND ka.place_ref_id IS NULL");
        }
    }

    @Test
    void noKindLeavesEveryQueryAsItWas() {
        for (String statement : List.of(CHECKINS + "findFeed", CHECKINS + "findFollowingFeed",
                REVIEWS + "findFeedReviews", TRIPS + "findFollowingTrips")) {
            assertThat(collapse(bound(statement, null).getSql())).as(statement).doesNotContain(" kp");
        }
        Map<String, Object> search = new HashMap<>();
        search.put("criteria", PublicTripSearchCriteria.of(null, null, null, null, null,
                true, null, true, true));
        search.put("limit", 20);
        search.put("offset", 0);
        assertThat(collapse(configuration.getMappedStatement(TRIPS + "searchPublicTripRanking")
                .getBoundSql(search).getSql())).doesNotContain(" kp");
    }

    @Test
    void theKindIsPartOfTheTripRankingKey() {
        PublicTripSearchCriteria all = PublicTripSearchCriteria.of(null, null, null, null, null,
                true, null, true, true);
        PublicTripSearchCriteria cafes = PublicTripSearchCriteria.of(null, null, null, null, null,
                true, null, true, true, FeedPlaceKind.of(null, List.of("CAFE")));

        assertThat(cafes).isNotEqualTo(all)
                .isEqualTo(PublicTripSearchCriteria.of(null, null, null, null, null,
                        true, null, true, true, FeedPlaceKind.of(null, List.of(" cafe "))));
    }

    private static BoundSql bound(String statementId, FeedPlaceKind kind) {
        return configuration.getMappedStatement(statementId).getBoundSql(params(kind));
    }

    private static Map<String, Object> params(FeedPlaceKind kind) {
        Map<String, Object> params = new HashMap<>();
        params.put("viewerId", UUID.randomUUID());
        params.put("excludeUserId", UUID.randomUUID());
        params.put("kind", kind);
        params.put("limit", 20);
        params.put("offset", 0);
        return params;
    }

    private static List<String> properties(BoundSql bound) {
        return bound.getParameterMappings().stream()
                .map(ParameterMapping::getProperty)
                .map(property -> property.replaceAll("^__frch_(\\w+?)_\\d+$", "$1"))
                .toList();
    }

    private static String collapse(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}

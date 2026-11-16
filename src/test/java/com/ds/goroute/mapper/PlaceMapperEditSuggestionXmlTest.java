package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import com.ds.goroute.type.PlaceBusinessStatus;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlaceMapper.xml for place edit suggestions (epic 09, V286), compiled without a database:
 * locked_fields stays out of the generic update, permanently closed places leave the AI and
 * trending candidates, and the list, pin and index projections carry the status text.
 */
@DisplayName("PlaceMapper.xml - edit suggestions and business status")
class PlaceMapperEditSuggestionXmlTest {

    private static final String NS = "com.ds.goroute.mapper.PlaceMapper.";
    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        String resource = "mapper/PlaceMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static String sql(String id, Map<String, Object> parameters) {
        return configuration.getMappedStatement(NS + id).getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void theGenericUpdateNeverWritesLockedFields() {
        assertThat(sql("update", params())).doesNotContain("locked_fields");
    }

    @Test
    void lockingAddsWithoutDuplicatesAndUnlockingRemovesOne() {
        assertThat(sql("addLockedFields", params("id", UUID.randomUUID(), "fields", new String[] {"PHONE"})))
                .contains("SELECT DISTINCT f FROM unnest(locked_fields ||");
        assertThat(sql("removeLockedField", params("id", UUID.randomUUID(), "field", "PHONE")))
                .contains("array_remove(locked_fields,");
    }

    @Test
    void permanentlyClosedPlacesLeaveTheAiAndTrendingCandidates() {
        String ai = sql("findActiveForAiWithinRadius", params(
                "latitude", BigDecimal.ONE, "longitude", BigDecimal.ONE, "radiusKm", BigDecimal.TEN,
                "areaId", null, "placeGroups", List.of(), "ids", List.of(), "limit", 10));
        String trending = sql("findTrendingUsage", params(
                "latitude", BigDecimal.ONE, "longitude", BigDecimal.ONE, "radiusMeters", BigDecimal.TEN,
                "placeGroups", List.of(), "days", 7, "excludeAiTrips", false, "limit", 10));

        assertThat(ai).contains("NOT ILIKE '%permanently closed%'");
        assertThat(trending).contains("NOT ILIKE '%permanently closed%'");
    }

    @Test
    void listPinAndIndexRowsCarryTheStatusText() {
        assertThat(sql("findListSummaryByIds", params("ids", List.of(UUID.randomUUID()), "publicOnly", true)))
                .contains("status");
        assertThat(sql("findMapPinsByIds", params("ids", List.of(UUID.randomUUID()))))
                .contains(", status FROM places");
        assertThat(sql("findSearchIndexRow", params("id", UUID.randomUUID()))).contains("checkin_count, status");
    }

    @Test
    void businessStatusReadsGooglesTextWhateverItsCase() {
        assertThat(PlaceBusinessStatus.fromStoredStatus("Permanently closed"))
                .isEqualTo(PlaceBusinessStatus.PERMANENTLY_CLOSED);
        assertThat(PlaceBusinessStatus.fromStoredStatus("TEMPORARILY CLOSED"))
                .isEqualTo(PlaceBusinessStatus.TEMPORARILY_CLOSED);
        assertThat(PlaceBusinessStatus.fromStoredStatus("")).isEqualTo(PlaceBusinessStatus.OPEN);
        assertThat(PlaceBusinessStatus.fromStoredStatus(null)).isEqualTo(PlaceBusinessStatus.OPEN);
        assertThat(PlaceBusinessStatus.fromStoredStatus("Open now")).isEqualTo(PlaceBusinessStatus.OPEN);
    }
}

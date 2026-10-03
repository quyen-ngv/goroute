package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import com.ds.goroute.dto.response.TrackResponses;
import com.ds.goroute.entity.PrivacyZone;
import com.ds.goroute.entity.TrackVisit;
import com.ds.goroute.entity.TripTrackSettings;
import com.ds.goroute.type.TrackVisitStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackMapperXmlTest {

    private static final String NAMESPACE = "com.ds.goroute.mapper.TrackMapper.";
    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        String resource = "mapper/TrackMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void daysAreFilteredByMembersAndOptionallyByDayNumber() {
        Map<String, Object> params = new HashMap<>();
        params.put("tripId", UUID.randomUUID());
        params.put("userIds", List.of(UUID.randomUUID(), UUID.randomUUID()));
        params.put("dayNumber", null);
        params.put("limit", 10);

        String withoutDay = sql("selectDays", params);
        params.put("dayNumber", 2);
        String withDay = sql("selectDays", params);

        assertThat(withoutDay).contains("AND user_id IN ( ? , ? )").doesNotContain("day_number = ?")
                .endsWith("ORDER BY user_id, local_date LIMIT ?");
        assertThat(withDay).contains("AND day_number = ?");
    }

    @Test
    void userScopedWritesCarryTheOwnerAndTheVersion() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("settings", TripTrackSettings.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).build());
        settings.put("expectedVersion", 3L);
        Map<String, Object> zone = new HashMap<>();
        zone.put("zone", PrivacyZone.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).build());
        zone.put("expectedVersion", 1L);

        assertThat(sql("updateSettings", settings)).endsWith("WHERE id = ? AND user_id = ? AND data_version = ?");
        assertThat(sql("updateZone", zone)).endsWith("WHERE id = ? AND user_id = ? AND data_version = ?");
        assertThat(sql("deleteZone", Map.of("id", UUID.randomUUID(), "userId", UUID.randomUUID())))
                .isEqualTo("DELETE FROM user_privacy_zones WHERE id = ? AND user_id = ?");
    }

    @Test
    void retentionDeletesOneBoundedBatchOfEndedTrips() {
        String sql = sql("deleteExpiredChunks", Map.of("lastTripEndDate", LocalDate.now(), "limit", 500));

        assertThat(sql).contains("JOIN trips t ON t.id = c.trip_id")
                .contains("WHERE t.end_date <= ?")
                .contains("LIMIT ?");
    }

    @Test
    void dayUpsertKeepsOneRowPerPersonAndDay() {
        assertThat(configuration.getMappedStatement(NAMESPACE + "upsertDay").getBoundSql(new HashMap<>())
                .getSql().replaceAll("\\s+", " "))
                .contains("ON CONFLICT (trip_id, user_id, local_date) DO UPDATE SET")
                .contains("data_version = trip_track_days.data_version + 1");
    }

    @Test
    void provincesComeFromOneIndexedIntersectionPerRebuild() {
        assertThat(sql("selectProvinceCodesForRoute", Map.of("wkt", "POINT(108.44 11.94)")))
                .contains("AND ST_Intersects(p.geom, ST_GeomFromText(?, 4326))")
                .contains("p.is_active = TRUE")
                .endsWith("ORDER BY p.code LIMIT 64");
        assertThat(sql("selectProvincesPassedByUser", Map.of("userId", UUID.randomUUID())))
                .contains("SELECT jsonb_array_elements_text(d.province_codes) FROM trip_track_days d WHERE d.user_id = ?")
                .endsWith("LIMIT 200");
        assertThat(configuration.getMappedStatement(NAMESPACE + "upsertDay").getBoundSql(new HashMap<>())
                .getSql().replaceAll("\\s+", " "))
                .contains("COALESCE(?::jsonb, '[]'::jsonb)")
                .contains("province_codes = EXCLUDED.province_codes");
    }

    @Test
    void offPlanWritesCarryTheOwnerAndTheKind() {
        TrackVisit visit = TrackVisit.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).build();
        Map<String, Object> status = new HashMap<>();
        status.put("id", UUID.randomUUID());
        status.put("userId", UUID.randomUUID());
        status.put("status", TrackVisitStatus.ADDED);
        status.put("createdActivityId", UUID.randomUUID());

        assertThat(sql("updateOffPlanStop", visit)).endsWith("WHERE id = ? AND user_id = ? AND kind = 'OFF_PLAN'");
        assertThat(sql("updateOffPlanStatus", status)).endsWith("WHERE id = ? AND user_id = ? AND kind = 'OFF_PLAN'");
        assertThat(sql("updateOffPlanNameIfMissing", Map.of("id", UUID.randomUUID(), "userId", UUID.randomUUID(),
                "name", "Cafe"))).endsWith("AND kind = 'OFF_PLAN' AND name IS NULL");
        assertThat(sql("deleteVisitById", Map.of("id", UUID.randomUUID(), "userId", UUID.randomUUID())))
                .isEqualTo("DELETE FROM trip_track_visits WHERE id = ? AND user_id = ?");
        assertThat(sql("selectVisitById", Map.of("id", UUID.randomUUID(), "tripId", UUID.randomUUID(),
                "userId", UUID.randomUUID()))).endsWith("WHERE id = ? AND trip_id = ? AND user_id = ?");
    }

    @Test
    void checkinReadsAreScopedToTheTripAndSkipRemovedOnes() {
        Map<String, Object> latest = new HashMap<>();
        latest.put("tripId", UUID.randomUUID());
        latest.put("userIds", List.of(UUID.randomUUID(), UUID.randomUUID()));
        latest.put("fromUtc", LocalDateTime.now());
        latest.put("toUtc", LocalDateTime.now());
        latest.put("limit", 2);
        Map<String, Object> own = Map.of("tripId", UUID.randomUUID(), "userId", UUID.randomUUID());

        assertThat(sql("selectLatestCheckins", latest))
                .contains("WHERE trip_id = ? AND is_removed = FALSE AND user_id IN ( ? , ? )")
                .contains("AND created_at >= ? AND created_at < ?")
                .endsWith("GROUP BY user_id ORDER BY user_id LIMIT ?");
        assertThat(sql("selectCheckedInActivityIds", own)).contains("activity_id IS NOT NULL").endsWith("LIMIT 1000");
        assertThat(sql("countCheckins", own))
                .endsWith("WHERE trip_id = ? AND user_id = ? AND is_removed = FALSE");
        assertThat(sql("selectDistanceByUser", Map.of("tripId", UUID.randomUUID(), "limit", 10)))
                .endsWith("GROUP BY user_id ORDER BY user_id LIMIT ?");
    }

    @Test
    void memberFlagSerialisesAsIsMe() throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                new TrackResponses.Member(UUID.randomUUID(), "Lan", null, true, false, true));

        assertThat(json).contains("\"isMe\":true").contains("\"hasTrack\":false").contains("\"visibleToMe\":true");
    }

    private static String sql(String id, Object params) {
        return configuration.getMappedStatement(NAMESPACE + id).getBoundSql(params).getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }
}

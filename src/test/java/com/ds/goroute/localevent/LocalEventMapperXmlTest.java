package com.ds.goroute.localevent;

import com.ds.goroute.config.database.UUIDTypeHandler;
import com.ds.goroute.localevent.persistence.LocalEventAdminSearchParams;
import com.ds.goroute.localevent.persistence.LocalEventDuplicateSearchParams;
import com.ds.goroute.localevent.persistence.LocalEventGeoSearchParams;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Compiles the local event SQL without a database and checks the predicates that carry the rules. */
class LocalEventMapperXmlTest {

    private static final String NAMESPACE = "com.ds.goroute.localevent.persistence.";

    @Test
    void publicSearchKeepsOnlyPublishedMappableOverlappingEventsInsideTheRadius() throws Exception {
        Configuration configuration = configuration("mapper/LocalEventMapper.xml");
        LocalEventGeoSearchParams params = geoParams(List.of("MUSIC", "FESTIVAL"));

        String list = sql(configuration, "LocalEventMapper.findPublicWithinRadius", Map.of("p", params));
        String count = sql(configuration, "LocalEventMapper.countPublicWithinRadius", Map.of("p", params));

        for (String statement : List.of(list, count)) {
            assertThat(statement)
                    .contains("e.is_deleted = FALSE")
                    .contains("e.publish_status = 'PUBLISHED'")
                    .contains("e.attendance_mode <> 'ONLINE'")
                    .contains("e.event_status <> 'MOVED_ONLINE'")
                    .contains("e.end_at > ?")
                    .contains("e.start_at < ?")
                    .contains("e.latitude BETWEEN ? AND ?")
                    .contains("e.longitude BETWEEN ? AND ?")
                    .contains("6371 * acos(")
                    .containsPattern("e\\.category IN \\(\\s*\\?\\s*,\\s*\\?\\s*\\)")
                    .doesNotContain("SELECT *")
                    .doesNotContain("CANCELLED");
        }
        assertThat(list)
                .contains("ORDER BY e.start_at ASC, e.id ASC")
                .contains("LIMIT ? OFFSET ?")
                .contains("jsonb_array_length(e.sessions) AS session_count")
                .contains("AS distance_km")
                .doesNotContain("e.description")
                .doesNotContain("e.raw_payload");
    }

    @Test
    void publicSearchWithoutCategoriesHasNoCategoryFilter() throws Exception {
        Configuration configuration = configuration("mapper/LocalEventMapper.xml");

        String list = sql(configuration, "LocalEventMapper.findPublicWithinRadius",
                Map.of("p", geoParams(List.of())));

        assertThat(list).doesNotContain("e.category IN");
    }

    @Test
    void publicDetailIsPublishedAndLiveOnly() throws Exception {
        Configuration configuration = configuration("mapper/LocalEventMapper.xml");

        String detail = sql(configuration, "LocalEventMapper.findPublishedById", Map.of("id", UUID.randomUUID()));

        assertThat(detail).contains("e.id = ?").contains("e.is_deleted = FALSE")
                .contains("e.publish_status = 'PUBLISHED'");
    }

    @Test
    void adminListAppliesEveryFilterAndAWhitelistedOrder() throws Exception {
        Configuration configuration = configuration("mapper/LocalEventMapper.xml");
        LocalEventAdminSearchParams params = LocalEventAdminSearchParams.builder()
                .search("Le hoi")
                .searchNorm("le hoi")
                .publishStatuses(List.of("DRAFT", "PUBLISHED"))
                .eventStatuses(List.of("CANCELLED"))
                .categories(List.of("MUSIC"))
                .source("ticketbox.vn")
                .provinceCode("79")
                .startFrom(LocalDateTime.of(2026, 10, 1, 0, 0))
                .startTo(LocalDateTime.of(2026, 11, 1, 0, 0))
                .duplicatesOnly(true)
                .sortKey("startAt")
                .descending(false)
                .limit(50)
                .offset(0)
                .build();

        String list = sql(configuration, "LocalEventMapper.findForAdmin", Map.of("p", params));

        assertThat(list)
                .contains("e.is_deleted = FALSE")
                .contains("e.title_norm LIKE ('%' || ? || '%') ESCAPE '\\'")
                .contains("e.external_id = ?")
                .containsPattern("e\\.publish_status IN \\(\\s*\\?\\s*,\\s*\\?\\s*\\)")
                .containsPattern("e\\.event_status IN \\(\\s*\\?\\s*\\)")
                .containsPattern("e\\.category IN \\(\\s*\\?\\s*\\)")
                .contains("e.source = ?")
                .contains("e.province_code = ?")
                .contains("e.start_at >= ?")
                .contains("e.start_at < ?")
                .contains("e.duplicate_of_event_id IS NOT NULL")
                .contains("ORDER BY e.start_at ASC, e.id ASC")
                .contains("LIMIT ? OFFSET ?")
                .doesNotContain("SELECT *");
    }

    @Test
    void adminListDefaultsToNewestEditFirst() throws Exception {
        Configuration configuration = configuration("mapper/LocalEventMapper.xml");
        LocalEventAdminSearchParams params = LocalEventAdminSearchParams.builder()
                .sortKey("updatedAt").descending(true).limit(50).offset(0).build();

        String list = sql(configuration, "LocalEventMapper.findForAdmin", Map.of("p", params));

        assertThat(list).contains("ORDER BY e.updated_at DESC, e.id DESC").doesNotContain("title_norm LIKE");
    }

    @Test
    void duplicateSearchUsesTrigramSimilarityWithinTheDayWindowNearbyOrSameProvince() throws Exception {
        Configuration configuration = configuration("mapper/LocalEventMapper.xml");
        LocalEventDuplicateSearchParams params = LocalEventDuplicateSearchParams.builder()
                .excludeId(UUID.randomUUID())
                .titleNorm("le hoi den hung")
                .minSimilarity(0.6)
                .startFrom(LocalDateTime.of(2026, 10, 9, 17, 0))
                .startTo(LocalDateTime.of(2026, 10, 12, 17, 0))
                .latitude(21.0).longitude(105.8).radiusKm(2)
                .minLat(20.98).maxLat(21.02).minLng(105.78).maxLng(105.82)
                .provinceCode("01")
                .limit(1)
                .build();

        String duplicates = sql(configuration, "LocalEventMapper.findDuplicateCandidates", Map.of("p", params));

        assertThat(duplicates)
                .contains("similarity(e.title_norm, ?) AS score")
                .contains("e.title_norm % ?")
                .contains("similarity(e.title_norm, ?) >= ?")
                .contains("e.is_deleted = FALSE")
                .contains("e.id <> ?")
                .contains("e.start_at >= ?")
                .contains("e.start_at < ?")
                .contains("OR e.province_code = ?")
                .contains("ORDER BY score DESC, e.start_at ASC, e.id ASC")
                .contains("LIMIT ?");
    }

    @Test
    void contentUpdateIsVersionGuardedAndNeverTouchesPublishStatus() throws Exception {
        Configuration configuration = configuration("mapper/LocalEventMapper.xml");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("e", com.ds.goroute.localevent.domain.LocalEvent.builder().id(UUID.randomUUID()).build());
        parameters.put("expectedVersion", 3L);

        String update = sql(configuration, "LocalEventMapper.updateContent", parameters);

        assertThat(update).contains("data_version = data_version + 1")
                .contains("WHERE id = ?")
                .contains("AND data_version = ?")
                .doesNotContain("publish_status");
    }

    @Test
    void theImageJobPollsBoundedPendingRowsOfLiveEventsOldestFirst() throws Exception {
        Configuration configuration = configuration("mapper/LocalEventMapper.xml");

        String pending = sql(configuration, "LocalEventMapper.findPendingImages",
                Map.of("maxAttempts", 3, "limit", 20));
        String done = sql(configuration, "LocalEventMapper.markImageDone",
                Map.of("id", UUID.randomUUID(), "url", "https://cdn/x.webp", "updatedAt", LocalDateTime.now()));
        String cover = sql(configuration, "LocalEventMapper.updateCoverFromImage",
                Map.of("eventId", UUID.randomUUID(), "imageId", UUID.randomUUID()));

        assertThat(pending).contains("i.status = 'PENDING'").contains("i.attempts < ?")
                .contains("e.is_deleted = FALSE").contains("ORDER BY i.created_at ASC, i.id ASC").contains("LIMIT ?");
        assertThat(done).contains("status = 'DONE'").contains("AND status = 'PENDING'");
        assertThat(cover).contains("i.position = 0").contains("i.status = 'DONE'").doesNotContain("data_version");
    }

    @Test
    void importBatchesAreBoundedAndNewestFirst() throws Exception {
        Configuration configuration = configuration("mapper/LocalEventImportMapper.xml");

        String batches = sql(configuration, "LocalEventImportMapper.findBatches", Map.of("limit", 50, "offset", 0));
        String items = sql(configuration, "LocalEventImportMapper.findItemsByBatch", Map.of("batchId", UUID.randomUUID()));

        assertThat(batches).contains("ORDER BY b.created_at DESC, b.id DESC").contains("LIMIT ? OFFSET ?")
                .doesNotContain("SELECT *");
        assertThat(items).contains("batch_id = ?").contains("ORDER BY item_index ASC, id ASC").contains("LIMIT 200");
    }

    @Test
    void apiKeyUseIsRecordedAtMostOncePerInterval() throws Exception {
        Configuration configuration = configuration("mapper/AdminApiKeyMapper.xml");

        String touch = sql(configuration, "AdminApiKeyMapper.touchLastUsed", Map.of(
                "id", UUID.randomUUID(), "usedAt", LocalDateTime.now(), "staleBefore", LocalDateTime.now()));
        String byHash = sql(configuration, "AdminApiKeyMapper.findByHash", Map.of("keyHash", "ab"));

        assertThat(touch).contains("last_used_at IS NULL OR last_used_at < ?");
        assertThat(byHash).contains("k.key_hash = ?").doesNotContain("SELECT *");
    }

    private static LocalEventGeoSearchParams geoParams(List<String> categories) {
        return LocalEventGeoSearchParams.builder()
                .latitude(10.77).longitude(106.70).radiusKm(30)
                .minLat(10.5).maxLat(11.0).minLng(106.4).maxLng(107.0)
                .from(LocalDateTime.of(2026, 10, 4, 0, 0))
                .to(LocalDateTime.of(2026, 12, 3, 0, 0))
                .categories(categories)
                .limit(20).offset(0)
                .build();
    }

    private static Configuration configuration(String resource) throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String sql(Configuration configuration, String statement, Object parameters) {
        return configuration.getMappedStatement(NAMESPACE + statement)
                .getBoundSql(parameters)
                .getSql()
                .replaceAll("\\s+", " ");
    }
}

package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceReviewMapperXmlTest {

    @Test
    void refreshCandidatesPrioritizeGoogleImagesAndSkipManagedOnlyPlaces() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        String resource = "mapper/PlaceReviewMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String sql = configuration.getMappedStatement(
                        "com.ds.goroute.mapper.PlaceReviewMapper.findRefreshCandidates")
                .getBoundSql(Map.of(
                        "cutoff", LocalDateTime.now().minusHours(24),
                        "includeRecent", false))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql)
                .contains("external_image_review_count")
                .contains("managed_image_review_count")
                .contains("COALESCE(rs.external_image_review_count, 0) > 0");
        assertThat(sql.indexOf("COALESCE(rs.google_image_review_count, 0) DESC"))
                .isLessThan(sql.indexOf("COALESCE(rs.recalculation_needed_count, 0) DESC"));
    }
}

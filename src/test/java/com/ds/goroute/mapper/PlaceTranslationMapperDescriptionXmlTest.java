package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.type.TranslationLocale;
import com.ds.goroute.type.TranslationSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * upsertDescription compiled without a database: it writes the description only, and only over a
 * row of the same or a lower source rank, so a Google refresh cannot overwrite a MANUAL text and
 * the stored name is never touched.
 */
@DisplayName("PlaceTranslationMapper.upsertDescription")
class PlaceTranslationMapperDescriptionXmlTest {

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        String resource = "mapper/PlaceTranslationMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void updatesTheDescriptionOnlyUnderTheRankRule() {
        PlaceTranslation translation = PlaceTranslation.builder()
                .placeId(UUID.randomUUID())
                .locale(TranslationLocale.VI)
                .name("Phở Thìn")
                .description("Quán phở bò lâu đời trên phố Lò Đúc.")
                .translationSource(TranslationSource.MANUAL)
                .build();

        String sql = configuration.getMappedStatement("com.ds.goroute.mapper.PlaceTranslationMapper.upsertDescription")
                .getBoundSql(translation).getSql().replaceAll("\\s+", " ");

        String update = sql.substring(sql.indexOf("DO UPDATE SET"));
        assertThat(update).contains("description = EXCLUDED.description")
                .contains("translation_source = EXCLUDED.translation_source")
                .doesNotContain("name = EXCLUDED.name");
        // The stored rank must not be higher than the incoming one: MANUAL (2) beats GOOGLE (1).
        assertThat(update).contains("WHEN 'MANUAL' THEN 2 WHEN 'GOOGLE' THEN 1")
                .contains("<=");
    }
}

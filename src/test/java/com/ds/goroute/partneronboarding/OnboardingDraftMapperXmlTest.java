package com.ds.goroute.partneronboarding;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The draft mapper is parsed and read as text, the same way the marketplace mappers are: a
 * broken statement would otherwise only surface when a partner saves a step in production.
 */
class OnboardingDraftMapperXmlTest {

    private static final String RESOURCE = "mapper/OnboardingDraftMapper.xml";
    private static final String NAMESPACE = "com.ds.goroute.partneronboarding.persistence.OnboardingDraftMapper.";

    @Test
    @DisplayName("Every statement the mapper interface declares is present and parses")
    void mapperXmlDeclaresEveryStatement() throws Exception {
        Configuration configuration = parse();

        assertThat(configuration.hasStatement(NAMESPACE + "insert")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "findById")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "findByUser")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "countByUser")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "updateStep")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "updateOrganization")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "updateStatus")).isTrue();
    }

    @Test
    @DisplayName("Every write is guarded by the version and refuses a draft that is no longer editable")
    void writesAreOptimisticallyLockedAndStatusGuarded() throws Exception {
        String xml = readXml();

        for (String statement : new String[]{"updateStep", "updateOrganization", "updateStatus"}) {
            String body = statementBody(xml, statement);
            assertThat(body)
                    .as("%s must compare the version the caller read", statement)
                    .contains("data_version = #{expectedVersion}");
            assertThat(body)
                    .as("%s must refuse a submitted or abandoned draft", statement)
                    .contains("status = 'DRAFT'");
            assertThat(body)
                    .as("%s must move the version on, or a stale write would be accepted twice", statement)
                    .contains("data_version = data_version + 1");
        }
    }

    @Test
    @DisplayName("JSON columns are cast, otherwise the driver rejects a String for jsonb")
    void jsonColumnsAreCast() throws Exception {
        String xml = readXml();

        assertThat(statementBody(xml, "insert")).contains("#{completedSteps}::jsonb", "#{data}::jsonb");
        assertThat(statementBody(xml, "updateStep")).contains("#{completedSteps}::jsonb", "#{data}::jsonb");
    }

    @Test
    @DisplayName("Listing drafts is bounded and deterministically ordered")
    void listingIsBoundedAndOrdered() throws Exception {
        String body = statementBody(readXml(), "findByUser");

        assertThat(body).contains("LIMIT #{limit}", "OFFSET #{offset}");
        assertThat(body).contains("ORDER BY updated_at DESC, id");
    }

    private Configuration parse() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String readXml() throws Exception {
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The whole statement, not just up to the first closing tag: dynamic SQL nests
     * {@code <where>} and {@code <if>}, and stopping at those would hide the clause under test.
     */
    private String statementBody(String xml, String id) {
        int start = xml.indexOf("id=\"" + id + "\"");
        assertThat(start).as("statement %s is declared", id).isNotNegative();
        int end = Integer.MAX_VALUE;
        for (String closing : new String[]{"</select>", "</insert>", "</update>", "</delete>"}) {
            int candidate = xml.indexOf(closing, start);
            if (candidate >= 0) {
                end = Math.min(end, candidate);
            }
        }
        assertThat(end).as("statement %s is closed", id).isNotEqualTo(Integer.MAX_VALUE);
        return xml.substring(start, end);
    }
}

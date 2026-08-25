package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperXmlTest {

    @Test
    void pagedReadsAndMutationsAreUserScoped() throws Exception {
        Configuration configuration = configuration();
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        String findSql = sql(configuration, "findByUserId", Map.of(
                "userId", userId,
                "tripId", tripId,
                "unreadOnly", true,
                "limit", 20,
                "offset", 0));
        String markSql = sql(configuration, "markAsRead", Map.of(
                "id", notificationId,
                "userId", userId));
        String deleteSql = sql(configuration, "deleteByIdAndUserId", Map.of(
                "id", notificationId,
                "userId", userId));

        assertThat(findSql)
                .contains("user_id = ?")
                .contains("trip_id = ?")
                .contains("is_read = FALSE")
                .contains("LIMIT ? OFFSET ?")
                .doesNotContain("SELECT *");
        assertThat(markSql).contains("id = ?").contains("user_id = ?");
        assertThat(deleteSql).contains("id = ?").contains("user_id = ?");
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        String resource = "mapper/NotificationMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String sql(Configuration configuration, String statement, Map<String, Object> parameters) {
        return configuration.getMappedStatement("com.ds.goroute.mapper.NotificationMapper." + statement)
                .getBoundSql(parameters)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }
}

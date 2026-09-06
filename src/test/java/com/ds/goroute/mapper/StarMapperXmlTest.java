package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StarMapperXmlTest {

    @Test
    void postBootstrapWalletReadClearsTheSessionCacheBeforeLoadingTheWallet() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        String resource = "mapper/StarMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        MappedStatement statement = configuration.getMappedStatement(
                "com.ds.goroute.mapper.StarMapper.findWalletAfterBootstrap");
        String sql = statement.getBoundSql(Map.of("userId", UUID.randomUUID()))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(statement.isFlushCacheRequired()).isTrue();
        assertThat(statement.isUseCache()).isFalse();
        assertThat(sql).isEqualTo(
                "SELECT user_id, balance, free_trip_quota_used, created_at, updated_at "
                        + "FROM user_star_wallets WHERE user_id = ?");
    }
}

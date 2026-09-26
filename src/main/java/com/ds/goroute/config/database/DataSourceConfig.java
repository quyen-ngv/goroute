package com.ds.goroute.config.database;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
// The second package is the partner-onboarding feature module, which keeps its own mapper
// next to the code that uses it rather than in the shared mapper package.
//
// annotationClass is not decoration: without it MyBatis registers *every* interface in
// these packages as a mapper, so a plain port interface sitting beside its mapper (as
// OnboardingDraftRepository does) becomes a second bean of its own type and the context
// refuses to start. Every mapper in both packages carries @Mapper already.
@MapperScan(basePackages = {"com.ds.goroute.mapper", "com.ds.goroute.partneronboarding.persistence",
        "com.ds.goroute.quest.persistence"},
        annotationClass = Mapper.class,
        sqlSessionFactoryRef = "sqlSessionFactory")
public class DataSourceConfig {

    /**
     * AbstractRoutingDataSource decides MASTER/SLAVE when a connection is first
     * requested. DataSourceTransactionManager asks for that connection before it
     * marks the transaction read-only, so without a lazy proxy every read-only
     * transaction still lands on MASTER. The proxy is opt-in because turning it on
     * sends read-only traffic to DB_SLAVE_URL, which changes what those queries see
     * when the replica lags. Default false keeps the pre-2026-09-16 behaviour.
     */
    @Value("${goroute.datasource.read-only-routing:false}")
    private boolean readOnlyRouting;

    /**
     * Applied to every mapper statement. A query with no timeout keeps its Hikari
     * connection out of the pool until the database kills it, so one stuck query
     * can starve the pool. Every batch job pages its work, so nothing legitimate
     * runs anywhere near this long.
     */
    @Value("${goroute.datasource.statement-timeout-seconds:30}")
    private int statementTimeoutSeconds;

    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master.hikari")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(name = "slaveDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.slave.hikari")
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(name = "routingDataSource")
    public DataSource routingDataSource(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("slaveDataSource") DataSource slaveDataSource) {

        ReplicationRoutingDataSource routingDataSource = new ReplicationRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("MASTER", masterDataSource);
        targetDataSources.put("SLAVE", slaveDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);

        if (!readOnlyRouting) {
            return routingDataSource;
        }
        routingDataSource.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    @Bean(name = "flyway")
    public Flyway flyway(@Qualifier("masterDataSource") DataSource masterDataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(masterDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean(name = "sqlSessionFactory")
    @Primary
    public SqlSessionFactory sqlSessionFactory(
            @Qualifier("routingDataSource") DataSource routingDataSource,
            @Qualifier("flyway") Flyway flyway) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(routingDataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
        bean.setTypeHandlersPackage("com.ds.goroute.config.database");
        bean.setTypeAliasesPackage("com.ds.goroute.entity");
        
        // This bean is the only MyBatis configuration source: defining it here makes
        // MybatisAutoConfiguration back off, so `mybatis.*` properties in
        // application.yaml are never read. Every setting must be applied below.
        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        // Use MyBatis built-in EnumTypeHandler (stores enum name as string)
        mybatisConfig.setDefaultEnumTypeHandler(org.apache.ibatis.type.EnumTypeHandler.class);
        mybatisConfig.setDefaultStatementTimeout(statementTimeoutSeconds);
        
        // Register UUID TypeHandler
        mybatisConfig.getTypeHandlerRegistry().register(java.util.UUID.class, UUIDTypeHandler.class);
        
        bean.setConfiguration(mybatisConfig);

        return bean.getObject();
    }

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(@Qualifier("routingDataSource") DataSource routingDataSource) {
        return new DataSourceTransactionManager(routingDataSource);
    }

    static class ReplicationRoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? "SLAVE" : "MASTER";
        }
    }
}

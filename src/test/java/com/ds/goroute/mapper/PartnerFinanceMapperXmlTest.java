package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the finance mapper the way MyBatis does at startup, so a broken dynamic tag or a statement
 * id that no longer matches the Java interface fails here rather than at the first request.
 */
class PartnerFinanceMapperXmlTest {
    private static final String RESOURCE = "mapper/PartnerFinanceMapper.xml";

    @Test
    void theFinanceMapperIsAValidMyBatisMapping() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        try (InputStream input = Resources.getResourceAsStream("mapper/HostOrganizationMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/HostOrganizationMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        for (String statement : new String[]{
                "stampHotelBookingCommission", "stampActivityOrderCommission",
                "findHotelBillableCandidates", "findActivityBillableCandidates",
                "insertStatement", "updateStatementTotals", "updateStatementStatus",
                "findStatementById", "findStatementByPeriod", "findLatestIssuedStatement",
                "findStatements", "countStatements", "insertLine", "findLines", "findLine",
                "deleteUndisputedLines", "openDispute", "resolveDispute", "countOpenDisputes"}) {
            assertTrue(configuration.hasStatement("com.ds.goroute.mapper.PartnerFinanceMapper." + statement),
                    "Missing statement: " + statement);
        }
        assertTrue(configuration.hasStatement("com.ds.goroute.mapper.HostOrganizationMapper.updateBillingProfile"));
    }

    @Test
    void theCommissionStampIsIdempotentAndLeavesTheBookingVersionAlone() throws Exception {
        String xml = read(RESOURCE);
        for (String id : new String[]{"stampHotelBookingCommission", "stampActivityOrderCommission"}) {
            String statement = statementOf(xml, "<update id=\"" + id + "\">", "</update>");
            // The guard is what makes a second call a no-op, so the booking service can call it freely.
            assertTrue(statement.contains("commission_percent IS NULL"),
                    id + " must only stamp an unstamped row");
            // Bumping data_version here would break a partner's concurrent optimistic-locked edit.
            assertTrue(!statement.contains("data_version"), id + " must not touch data_version");
        }
    }

    @Test
    void acceptingADisputeZeroesOnlyAnOpenLine() throws Exception {
        String statement = statementOf(read(RESOURCE), "<update id=\"resolveDispute\">", "</update>");
        assertTrue(statement.contains("commission_amount = 0, net_amount = gross_amount"));
        assertTrue(statement.contains("dispute_status = 'OPEN'"));
    }

    @Test
    void regenerationOnlyDeletesLinesNobodyContested() throws Exception {
        String statement = statementOf(read(RESOURCE), "<delete id=\"deleteUndisputedLines\">", "</delete>");
        assertTrue(statement.contains("dispute_status = 'NONE'"));
    }

    private String read(String resource) throws Exception {
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String statementOf(String xml, String open, String close) {
        int start = xml.indexOf(open);
        assertTrue(start >= 0, "Statement not found: " + open);
        return xml.substring(start, xml.indexOf(close, start));
    }
}

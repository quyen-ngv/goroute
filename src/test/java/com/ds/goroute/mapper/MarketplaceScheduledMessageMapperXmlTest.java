package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trigger-time branches are dynamic SQL, so they are asserted by rendering them rather than by
 * reading the file: a typo in a {@code <choose>} test would otherwise silently fall through to the
 * confirmation branch and send every message at the wrong moment.
 */
class MarketplaceScheduledMessageMapperXmlTest {
    private static final String NS = "com.ds.goroute.mapper.MarketplaceScheduledMessageMapper.";
    private Configuration configuration;

    @BeforeEach
    void parse() throws Exception {
        configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        String resource = "mapper/MarketplaceScheduledMessageMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private String sql(String statement, String trigger) {
        Map<String, Object> params = new HashMap<>();
        params.put("scheduledMessageId", UUID.randomUUID());
        params.put("organizationId", UUID.randomUUID());
        params.put("trigger", trigger);
        params.put("offsetHours", 24);
        params.put("now", LocalDateTime.of(2026, 9, 1, 8, 0));
        params.put("serverZone", "Asia/Ho_Chi_Minh");
        params.put("lookbackHours", 48);
        params.put("limit", 50);
        BoundSql bound = configuration.getMappedStatement(NS + statement).getBoundSql(params);
        return bound.getSql().replaceAll("\\s+", " ");
    }

    @Test
    void everyStatementTheServiceAndJobCallIsMapped() {
        for (String id : new String[]{"insert", "update", "delete", "findById", "findByOrganization",
                "countByOrganization", "findEnabled", "findDueHotelTargets", "findDueActivityTargets",
                "findHotelBookingStatus", "findActivityOrderStatus", "insertRun", "updateRunOutcome", "findRuns"}) {
            assertTrue(configuration.hasStatement(NS + id), id + " is not mapped");
        }
    }

    @Test
    void checkInTriggerSubtractsTheOffsetOnThePropertyClock() {
        String sql = sql("findDueHotelTargets", "BEFORE_CHECK_IN");
        assertTrue(sql.contains("check_in_date + COALESCE(h.check_in_time, TIME '14:00')"));
        assertTrue(sql.contains("- (? * INTERVAL '1 hour')"));
        assertTrue(sql.contains("AT TIME ZONE"), "must compare in the property's own timezone");
        assertFalse(sql.contains("check_out_time"));
        assertFalse(sql.contains("marketplace_entity_versions"));
    }

    @Test
    void checkOutTriggerAddsTheOffsetInsteadOfSubtractingIt() {
        String sql = sql("findDueHotelTargets", "AFTER_CHECK_OUT");
        assertTrue(sql.contains("check_out_date + COALESCE(h.check_out_time, TIME '12:00')"));
        assertTrue(sql.contains("+ (? * INTERVAL '1 hour')"));
        assertFalse(sql.contains("check_in_time"));
    }

    @Test
    void confirmationTriggerUsesTheHistoryRowAndNoTimezoneConversion() {
        String sql = sql("findDueHotelTargets", "ON_BOOKING_CONFIRMED");
        assertTrue(sql.contains("v.entity_type = 'HOTEL_BOOKING'"));
        assertTrue(sql.contains("v.snapshot ->> 'bookingStatus' = 'CONFIRMED'"));
        // Confirmation time is already a server-local column; converting it would shift every send.
        assertFalse(sql.contains("AT TIME ZONE"));
    }

    @Test
    void activityTriggersSplitTheSameWayAndSkipOrdersWithoutASlot() {
        String beforeActivity = sql("findDueActivityTargets", "BEFORE_ACTIVITY");
        assertTrue(beforeActivity.contains("s.starts_at IS NOT NULL"));
        assertTrue(beforeActivity.contains("s.starts_at - (? * INTERVAL '1 hour')"));
        assertTrue(beforeActivity.contains("AT TIME ZONE"));

        String onConfirmed = sql("findDueActivityTargets", "ON_BOOKING_CONFIRMED");
        assertTrue(onConfirmed.contains("v.entity_type = 'ACTIVITY_ORDER'"));
        assertTrue(onConfirmed.contains("v.snapshot ->> 'orderStatus' = 'CONFIRMED'"));
        assertFalse(onConfirmed.contains("AT TIME ZONE"));
    }

    @Test
    void everySelectionExcludesBookingsThatAlreadyHaveARunRowAndUndeliverableStatuses() {
        for (String trigger : new String[]{"BEFORE_CHECK_IN", "AFTER_CHECK_OUT", "ON_BOOKING_CONFIRMED"}) {
            String sql = sql("findDueHotelTargets", trigger);
            assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM marketplace_scheduled_message_runs run"),
                    trigger + " must not re-send a booking that already has a run row");
            assertTrue(sql.contains("booking_status IN ('CONFIRMED','CHECKED_IN','COMPLETED')"), trigger);
        }
        String activity = sql("findDueActivityTargets", "BEFORE_ACTIVITY");
        assertTrue(activity.contains("NOT EXISTS (SELECT 1 FROM marketplace_scheduled_message_runs run"));
        assertTrue(activity.contains("order_status IN ('CONFIRMED','CHECKED_IN','COMPLETED')"));
    }

    @Test
    void everyCallerControlledValueIsBoundNeverInterpolated() {
        for (String statement : new String[]{"findDueHotelTargets", "findDueActivityTargets"}) {
            for (String trigger : new String[]{"BEFORE_CHECK_IN", "AFTER_CHECK_OUT", "BEFORE_ACTIVITY", "ON_BOOKING_CONFIRMED"}) {
                assertFalse(sql(statement, trigger).contains("${"), statement + "/" + trigger);
            }
        }
    }
}

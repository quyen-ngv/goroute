package com.ds.goroute.mapper;

import com.ds.goroute.entity.BillableBookingRow;
import com.ds.goroute.entity.PartnerStatement;
import com.ds.goroute.entity.PartnerStatementLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * SQL for partner finance: the commission stamp on a booking row, and the statement/line aggregate.
 *
 * <p>The two {@code stamp…} updates are the only writes this module makes to {@code hotel_bookings}
 * / {@code activity_orders}. They are deliberately narrow (three accounting columns, guarded by
 * {@code commission_percent IS NULL}) and do <b>not</b> bump {@code data_version}: freezing the
 * commission is bookkeeping, not a business change, and it must never make a partner's concurrent
 * edit of the same booking fail its optimistic-lock check.
 */
@Mapper
public interface PartnerFinanceMapper {

    // --- Commission stamping -------------------------------------------------------------------

    UUID findHotelBookingOrganization(@Param("id") UUID bookingId);

    UUID findActivityOrderOrganization(@Param("id") UUID orderId);

    int stampHotelBookingCommission(@Param("id") UUID bookingId,
                                    @Param("commissionPercent") BigDecimal commissionPercent,
                                    @Param("ruleVersion") String ruleVersion);

    int stampActivityOrderCommission(@Param("id") UUID orderId,
                                     @Param("commissionPercent") BigDecimal commissionPercent,
                                     @Param("ruleVersion") String ruleVersion);

    // --- Billable candidates -------------------------------------------------------------------

    List<BillableBookingRow> findHotelBillableCandidates(@Param("organizationId") UUID organizationId,
                                                         @Param("periodStart") LocalDate periodStart,
                                                         @Param("periodEnd") LocalDate periodEnd);

    List<BillableBookingRow> findActivityBillableCandidates(@Param("organizationId") UUID organizationId,
                                                             @Param("periodStart") LocalDate periodStart,
                                                             @Param("periodEnd") LocalDate periodEnd);

    // --- Statements ----------------------------------------------------------------------------

    int insertStatement(PartnerStatement statement);

    int updateStatementTotals(PartnerStatement statement);

    int updateStatementStatus(@Param("id") UUID statementId, @Param("status") String status,
                              @Param("issuedAt") LocalDateTime issuedAt,
                              @Param("settledAt") LocalDateTime settledAt,
                              @Param("note") String note,
                              @Param("updatedAt") LocalDateTime updatedAt);

    PartnerStatement findStatementById(@Param("id") UUID statementId);

    PartnerStatement findStatementByPeriod(@Param("organizationId") UUID organizationId,
                                           @Param("periodStart") LocalDate periodStart,
                                           @Param("periodEnd") LocalDate periodEnd);

    PartnerStatement findLatestIssuedStatement(@Param("organizationId") UUID organizationId);

    List<PartnerStatement> findStatements(@Param("organizationId") UUID organizationId,
                                          @Param("limit") int limit, @Param("offset") int offset);

    long countStatements(@Param("organizationId") UUID organizationId);

    // --- Lines and disputes --------------------------------------------------------------------

    int insertLine(PartnerStatementLine line);

    List<PartnerStatementLine> findLines(@Param("statementId") UUID statementId);

    PartnerStatementLine findLine(@Param("id") UUID lineId);

    /** Drops the recomputable lines only; anything a partner already contested is preserved. */
    int deleteUndisputedLines(@Param("statementId") UUID statementId);

    int openDispute(@Param("id") UUID lineId, @Param("reason") String reason,
                    @Param("openedAt") LocalDateTime openedAt);

    int resolveDispute(@Param("id") UUID lineId, @Param("status") String status,
                       @Param("note") String note, @Param("resolvedBy") UUID resolvedBy,
                       @Param("resolvedAt") LocalDateTime resolvedAt,
                       @Param("zeroCommission") boolean zeroCommission);

    long countOpenDisputes(@Param("organizationId") UUID organizationId);
}

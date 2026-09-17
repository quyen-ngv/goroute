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

    /**
     * @param excludeStatementId when set, bookings already carried by a <b>different</b> statement
     *                           are left out, so two overlapping periods cannot bill the same stay
     *                           twice. Null asks for the unfiltered list (the partner's preview of
     *                           a period, which is not a bill).
     */
    List<BillableBookingRow> findHotelBillableCandidates(@Param("organizationId") UUID organizationId,
                                                         @Param("periodStart") LocalDate periodStart,
                                                         @Param("periodEnd") LocalDate periodEnd,
                                                         @Param("excludeStatementId") UUID excludeStatementId);

    /** @see #findHotelBillableCandidates(UUID, LocalDate, LocalDate, UUID) */
    List<BillableBookingRow> findActivityBillableCandidates(@Param("organizationId") UUID organizationId,
                                                             @Param("periodStart") LocalDate periodStart,
                                                             @Param("periodEnd") LocalDate periodEnd,
                                                             @Param("excludeStatementId") UUID excludeStatementId);

    // --- Statements ----------------------------------------------------------------------------

    int insertStatement(PartnerStatement statement);

    int updateStatementTotals(PartnerStatement statement);

    int updateStatementStatus(@Param("id") UUID statementId, @Param("status") String status,
                              @Param("issuedAt") LocalDateTime issuedAt,
                              @Param("settledAt") LocalDateTime settledAt,
                              @Param("note") String note,
                              @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Settles a statement only if it is still settleable: issued, not already settled, and with no
     * line under dispute. The three conditions are the same ones the service checks before calling;
     * carrying them in the UPDATE is what stops a dispute opened between the check and the write
     * from landing on an already-settled period.
     *
     * @return 1 when this call settled it, 0 when it was no longer settleable
     */
    int settleStatement(@Param("id") UUID statementId,
                        @Param("settledAt") LocalDateTime settledAt,
                        @Param("note") String note,
                        @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Takes the statement's row lock, so that settling, disputing and regenerating queue behind
     * each other rather than each writing against a statement the other has since changed.
     *
     * @return the id when the statement exists and is now locked, null when it does not
     */
    UUID lockStatement(@Param("id") UUID statementId);

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

package com.ds.goroute.repository;

import com.ds.goroute.entity.BillableBookingRow;
import com.ds.goroute.entity.PartnerStatement;
import com.ds.goroute.entity.PartnerStatementLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for partner finance: commission stamps, statements, statement lines and disputes. */
public interface PartnerFinanceRepository {

    Optional<UUID> findHotelBookingOrganization(UUID bookingId);

    Optional<UUID> findActivityOrderOrganization(UUID orderId);

    int stampHotelBookingCommission(UUID bookingId, BigDecimal commissionPercent, String ruleVersion);

    int stampActivityOrderCommission(UUID orderId, BigDecimal commissionPercent, String ruleVersion);

    /**
     * @param excludeStatementId when set, bookings already carried by another statement are left
     *                           out; null asks for the unfiltered list
     */
    List<BillableBookingRow> findHotelBillableCandidates(UUID organizationId, LocalDate periodStart, LocalDate periodEnd,
                                                         UUID excludeStatementId);

    /** @see #findHotelBillableCandidates(UUID, LocalDate, LocalDate, UUID) */
    List<BillableBookingRow> findActivityBillableCandidates(UUID organizationId, LocalDate periodStart, LocalDate periodEnd,
                                                            UUID excludeStatementId);

    int insertStatement(PartnerStatement statement);

    int updateStatementTotals(PartnerStatement statement);

    int updateStatementStatus(UUID statementId, String status, LocalDateTime issuedAt, LocalDateTime settledAt,
                              String note, LocalDateTime updatedAt);

    /** Settles only while the statement is still issued, unsettled and free of open disputes. */
    int settleStatement(UUID statementId, LocalDateTime settledAt, String note, LocalDateTime updatedAt);

    /** Takes the statement's row lock; false when there is no such statement. */
    boolean lockStatement(UUID statementId);

    Optional<PartnerStatement> findStatementById(UUID statementId);

    Optional<PartnerStatement> findStatementByPeriod(UUID organizationId, LocalDate periodStart, LocalDate periodEnd);

    Optional<PartnerStatement> findLatestIssuedStatement(UUID organizationId);

    List<PartnerStatement> findStatements(UUID organizationId, int limit, int offset);

    long countStatements(UUID organizationId);

    int insertLine(PartnerStatementLine line);

    List<PartnerStatementLine> findLines(UUID statementId);

    Optional<PartnerStatementLine> findLine(UUID lineId);

    int deleteUndisputedLines(UUID statementId);

    int openDispute(UUID lineId, String reason, LocalDateTime openedAt);

    int resolveDispute(UUID lineId, String status, String note, UUID resolvedBy, LocalDateTime resolvedAt,
                       boolean zeroCommission);

    long countOpenDisputes(UUID organizationId);
}

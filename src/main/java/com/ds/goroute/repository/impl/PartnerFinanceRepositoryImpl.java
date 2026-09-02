package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.BillableBookingRow;
import com.ds.goroute.entity.PartnerStatement;
import com.ds.goroute.entity.PartnerStatementLine;
import com.ds.goroute.mapper.PartnerFinanceMapper;
import com.ds.goroute.repository.PartnerFinanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PartnerFinanceRepositoryImpl implements PartnerFinanceRepository {
    private final PartnerFinanceMapper mapper;

    @Override public Optional<UUID> findHotelBookingOrganization(UUID bookingId) {
        return Optional.ofNullable(mapper.findHotelBookingOrganization(bookingId));
    }

    @Override public Optional<UUID> findActivityOrderOrganization(UUID orderId) {
        return Optional.ofNullable(mapper.findActivityOrderOrganization(orderId));
    }

    @Override public int stampHotelBookingCommission(UUID bookingId, BigDecimal commissionPercent, String ruleVersion) {
        return mapper.stampHotelBookingCommission(bookingId, commissionPercent, ruleVersion);
    }

    @Override public int stampActivityOrderCommission(UUID orderId, BigDecimal commissionPercent, String ruleVersion) {
        return mapper.stampActivityOrderCommission(orderId, commissionPercent, ruleVersion);
    }

    @Override public List<BillableBookingRow> findHotelBillableCandidates(UUID organizationId, LocalDate periodStart, LocalDate periodEnd) {
        return mapper.findHotelBillableCandidates(organizationId, periodStart, periodEnd);
    }

    @Override public List<BillableBookingRow> findActivityBillableCandidates(UUID organizationId, LocalDate periodStart, LocalDate periodEnd) {
        return mapper.findActivityBillableCandidates(organizationId, periodStart, periodEnd);
    }

    @Override public int insertStatement(PartnerStatement statement) { return mapper.insertStatement(statement); }

    @Override public int updateStatementTotals(PartnerStatement statement) { return mapper.updateStatementTotals(statement); }

    @Override public int updateStatementStatus(UUID statementId, String status, LocalDateTime issuedAt,
            LocalDateTime settledAt, String note, LocalDateTime updatedAt) {
        return mapper.updateStatementStatus(statementId, status, issuedAt, settledAt, note, updatedAt);
    }

    @Override public Optional<PartnerStatement> findStatementById(UUID statementId) {
        return Optional.ofNullable(mapper.findStatementById(statementId));
    }

    @Override public Optional<PartnerStatement> findStatementByPeriod(UUID organizationId, LocalDate periodStart, LocalDate periodEnd) {
        return Optional.ofNullable(mapper.findStatementByPeriod(organizationId, periodStart, periodEnd));
    }

    @Override public Optional<PartnerStatement> findLatestIssuedStatement(UUID organizationId) {
        return Optional.ofNullable(mapper.findLatestIssuedStatement(organizationId));
    }

    @Override public List<PartnerStatement> findStatements(UUID organizationId, int limit, int offset) {
        return mapper.findStatements(organizationId, limit, offset);
    }

    @Override public long countStatements(UUID organizationId) { return mapper.countStatements(organizationId); }

    @Override public int insertLine(PartnerStatementLine line) { return mapper.insertLine(line); }

    @Override public List<PartnerStatementLine> findLines(UUID statementId) { return mapper.findLines(statementId); }

    @Override public Optional<PartnerStatementLine> findLine(UUID lineId) { return Optional.ofNullable(mapper.findLine(lineId)); }

    @Override public int deleteUndisputedLines(UUID statementId) { return mapper.deleteUndisputedLines(statementId); }

    @Override public int openDispute(UUID lineId, String reason, LocalDateTime openedAt) {
        return mapper.openDispute(lineId, reason, openedAt);
    }

    @Override public int resolveDispute(UUID lineId, String status, String note, UUID resolvedBy,
            LocalDateTime resolvedAt, boolean zeroCommission) {
        return mapper.resolveDispute(lineId, status, note, resolvedBy, resolvedAt, zeroCommission);
    }

    @Override public long countOpenDisputes(UUID organizationId) { return mapper.countOpenDisputes(organizationId); }
}

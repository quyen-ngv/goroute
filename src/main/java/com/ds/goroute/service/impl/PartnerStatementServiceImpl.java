package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.PartnerFinanceSummaryResponse;
import com.ds.goroute.dto.response.PartnerStatementLineResponse;
import com.ds.goroute.dto.response.PartnerStatementResponse;
import com.ds.goroute.entity.BillableBookingRow;
import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationMember;
import com.ds.goroute.entity.PartnerStatement;
import com.ds.goroute.entity.PartnerStatementLine;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.PartnerFinanceRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.MarketplaceCommissionService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.PartnerStatementService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MarketplaceBookingType;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.OrganizationMemberStatus;
import com.ds.goroute.type.PartnerRole;
import com.ds.goroute.type.PartnerStatementLineReason;
import com.ds.goroute.type.PartnerStatementStatus;
import com.ds.goroute.type.StatementDisputeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds, issues and disputes monthly partner statements.
 *
 * <p>Two rules carry the whole feature and are kept in one readable place each:
 * <ul>
 *   <li>{@link #billableLine(BillableBookingRow)} — what the platform is allowed to charge for.</li>
 *   <li>{@link #recompute(PartnerStatement, String)} — totals are always the sum of the surviving
 *       lines, never a patched number, so accepting a dispute cannot leave a statement inconsistent.</li>
 * </ul>
 *
 * <p>No money moves: a statement is a record, {@code SETTLED} is an operator assertion that the
 * period was reconciled outside the platform.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerStatementServiceImpl implements PartnerStatementService {

    /** "… — penalty 450000.00 VND (PERCENT_50)" as written by the cancel use cases. */
    private static final Pattern PENALTY_AMOUNT = Pattern.compile(
            "penalty\\s+([0-9][0-9,]*(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter PERIOD_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Set<String> STATEMENT_RECIPIENT_ROLES =
            Set.of(PartnerRole.FINANCE.name(), PartnerRole.PARTNER_ADMIN.name());
    private static final int MAX_PAGE_SIZE = 100;
    private static final String CSV_HEADER =
            "bookingType,bookingCode,guestName,serviceDate,grossAmount,commissionPercent,"
                    + "commissionAmount,netAmount,lineReason,disputeStatus,currency";

    private final PartnerFinanceRepository finance;
    private final HostOrganizationRepository organizations;
    private final UserRepository users;
    private final PartnerAuthorizationService authorization;
    private final MarketplaceCommissionService commissionService;
    private final MarketplaceHistoryService history;
    private final NotificationService notificationService;
    private final NotificationTemplateRenderer templateRenderer;
    private final BusinessConfigService businessConfig;

    // --- Partner console ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PartnerStatementResponse> partnerList(UUID actorUserId, UUID organizationId, int page, int size) {
        requireFinanceRead(organizationId, actorUserId);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        List<PartnerStatementResponse> items = finance
                .findStatements(organizationId, safeSize, safePage * safeSize).stream()
                .map(statement -> toResponse(statement, null))
                .toList();
        return PageResponse.of(items, finance.countStatements(organizationId), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerStatementResponse partnerGet(UUID actorUserId, UUID organizationId, UUID statementId) {
        requireFinanceRead(organizationId, actorUserId);
        PartnerStatement statement = statementOf(organizationId, statementId);
        return toResponse(statement, finance.findLines(statementId));
    }

    @Override
    @Transactional(readOnly = true)
    public String partnerExportCsv(UUID actorUserId, UUID organizationId, UUID statementId) {
        requireFinanceRead(organizationId, actorUserId);
        PartnerStatement statement = statementOf(organizationId, statementId);
        StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
        for (PartnerStatementLine line : finance.findLines(statementId)) {
            csv.append(csvCell(line.getBookingType())).append(',')
               .append(csvCell(line.getBookingCode())).append(',')
               .append(csvCell(line.getGuestName())).append(',')
               .append(csvCell(line.getServiceDate())).append(',')
               .append(csvCell(line.getGrossAmount())).append(',')
               .append(csvCell(line.getCommissionPercent())).append(',')
               .append(csvCell(line.getCommissionAmount())).append(',')
               .append(csvCell(line.getNetAmount())).append(',')
               .append(csvCell(line.getLineReason())).append(',')
               .append(csvCell(line.getDisputeStatus())).append(',')
               .append(csvCell(statement.getCurrency())).append('\n');
        }
        return csv.toString();
    }

    @Override
    @Transactional
    public PartnerStatementResponse partnerOpenDispute(UUID actorUserId, UUID organizationId, UUID statementId,
            UUID lineId, String reason) {
        // Opening a dispute is a financial act, so it takes the reporting permission specifically —
        // plain ORGANIZATION_READ is enough to look at a statement but not to contest one.
        authorization.requirePermission(organizationId, actorUserId, "REPORT_READ");
        // Taken before the statement is read, so "is it already settled" is still true when the
        // dispute is written: an operator settling this very statement is holding the same row.
        finance.lockStatement(statementId);
        PartnerStatement statement = statementOf(organizationId, statementId);
        String cleanedReason = blankToNull(reason);
        if (cleanedReason == null) throw new BusinessException(ErrorConstant.BAD_REQUEST, "A dispute reason is required");
        if (statement.getIssuedAt() == null) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "This statement has not been issued yet");
        }
        if (PartnerStatementStatus.SETTLED.name().equals(statement.getStatus())) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "This statement is already settled");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = disputeWindowEnd(statement);
        if (windowEnd != null && now.isAfter(windowEnd)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "The " + disputeWindowDays() + "-day dispute window for this statement has closed");
        }
        PartnerStatementLine line = lineOf(statementId, lineId);
        if (!StatementDisputeStatus.NONE.name().equals(line.getDisputeStatus())) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This line already has a dispute");
        }
        if (finance.openDispute(lineId, cleanedReason, now) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This line already has a dispute");
        }
        finance.updateStatementStatus(statementId, PartnerStatementStatus.DISPUTED.name(), null, null, null, now);
        history.record(organizationId, "PARTNER_STATEMENT_LINE", lineId, "DISPUTE_OPENED", line,
                List.of("disputeStatus"), actorUserId, "USER", cleanedReason);
        return partnerGetInternal(organizationId, statementId);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerFinanceSummaryResponse partnerSummary(UUID actorUserId, UUID organizationId) {
        HostOrganization organization = requireFinanceRead(organizationId, actorUserId);
        LocalDate today = todayIn(organization);
        LocalDate periodStart = today.withDayOfMonth(1);
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
        List<LineDraft> drafts = billableDrafts(organizationId, periodStart, periodEnd, null);
        BigDecimal defaultPercent = commissionService.currentCommissionPercent(organizationId);
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        for (LineDraft draft : drafts) {
            BigDecimal percent = draft.commissionPercent() == null ? defaultPercent : draft.commissionPercent();
            gross = gross.add(draft.gross());
            commission = commission.add(commissionOf(draft.gross(), percent));
        }
        PartnerStatement last = finance.findLatestIssuedStatement(organizationId).orElse(null);
        return PartnerFinanceSummaryResponse.builder()
                .periodStart(periodStart).periodEnd(periodEnd).currency(organization.getDefaultCurrency())
                .grossAmount(scaled(gross)).commissionAmount(scaled(commission))
                .netAmount(scaled(gross.subtract(commission))).bookingCount(drafts.size())
                .openDisputes((int) finance.countOpenDisputes(organizationId))
                .commissionPercent(defaultPercent)
                .lastStatement(last == null ? null : toResponse(last, null))
                .build();
    }

    // --- Admin ----------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PartnerStatementResponse> adminList(UUID organizationId, int page, int size) {
        organizationOf(organizationId);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        List<PartnerStatementResponse> items = finance
                .findStatements(organizationId, safeSize, safePage * safeSize).stream()
                .map(statement -> toResponse(statement, null))
                .toList();
        return PageResponse.of(items, finance.countStatements(organizationId), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerStatementResponse adminGet(UUID organizationId, UUID statementId) {
        PartnerStatement statement = statementOf(organizationId, statementId);
        return toResponse(statement, finance.findLines(statementId));
    }

    @Override
    @Transactional
    public PartnerStatementResponse adminGenerate(UUID actorUserId, UUID organizationId,
            LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "periodEnd must be on or after periodStart");
        }
        HostOrganization organization = organizationOf(organizationId);
        return generateAndIssue(organization, periodStart, periodEnd, actorUserId, "ADMIN");
    }

    @Override
    @Transactional
    public PartnerStatementResponse adminResolveDispute(UUID actorUserId, UUID statementId, UUID lineId,
            boolean accept, String note) {
        // Statement first, then the line: the same order settling and disputing take them in, so
        // two of them queue instead of deadlocking.
        finance.lockStatement(statementId);
        PartnerStatement statement = finance.findStatementById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Statement not found"));
        PartnerStatementLine line = lineOf(statementId, lineId);
        if (!StatementDisputeStatus.OPEN.name().equals(line.getDisputeStatus())) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This dispute is not open");
        }
        String cleanedNote = blankToNull(note);
        if (!accept && cleanedNote == null) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Rejecting a dispute requires a note");
        }
        StatementDisputeStatus target = accept ? StatementDisputeStatus.ACCEPTED : StatementDisputeStatus.REJECTED;
        LocalDateTime now = LocalDateTime.now();
        if (finance.resolveDispute(lineId, target.name(), cleanedNote, actorUserId, now, accept) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This dispute was already resolved");
        }
        recompute(statement, statement.getCurrency());
        history.record(statement.getOrganizationId(), "PARTNER_STATEMENT_LINE", lineId,
                accept ? "DISPUTE_ACCEPTED" : "DISPUTE_REJECTED", line, List.of("disputeStatus", "commissionAmount"),
                actorUserId, "ADMIN", cleanedNote);
        return adminGet(statement.getOrganizationId(), statementId);
    }

    @Override
    @Transactional
    public PartnerStatementResponse adminUpdateStatus(UUID actorUserId, UUID statementId,
            PartnerStatementStatus status, String note) {
        // Held for the rest of this transaction. Settling is a read of the lines followed by a
        // write against what was read, and a dispute opened in between would otherwise land on a
        // period that had just been agreed.
        finance.lockStatement(statementId);
        PartnerStatement statement = finance.findStatementById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Statement not found"));
        if (status != PartnerStatementStatus.SETTLED) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "Only SETTLED can be set by hand; the other states are computed from the lines");
        }
        if (PartnerStatementStatus.SETTLED.name().equals(statement.getStatus())) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This statement is already settled");
        }
        if (statement.getIssuedAt() == null) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Issue the statement before settling it");
        }
        if (finance.findLines(statementId).stream()
                .anyMatch(line -> StatementDisputeStatus.OPEN.name().equals(line.getDisputeStatus()))) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Resolve every open dispute before settling");
        }
        LocalDateTime now = LocalDateTime.now();
        // The same three conditions again, this time inside the UPDATE. The checks above stay so
        // the operator is told which one stopped them; this one is what makes the answer still
        // true at the moment it is written.
        if (finance.settleStatement(statementId, now, blankToNull(note), now) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "This statement changed while it was being settled; reload it");
        }
        history.record(statement.getOrganizationId(), "PARTNER_STATEMENT", statementId, "SETTLED", statement,
                List.of("status"), actorUserId, "ADMIN", blankToNull(note));
        return adminGet(statement.getOrganizationId(), statementId);
    }

    // --- Job ------------------------------------------------------------------------------------

    @Override
    @Transactional
    public PartnerStatementResponse issuePreviousMonth(UUID organizationId) {
        HostOrganization organization = organizationOf(organizationId);
        LocalDate periodStart = todayIn(organization).withDayOfMonth(1).minusMonths(1);
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
        return generateAndIssue(organization, periodStart, periodEnd, null, "SYSTEM");
    }

    // --- Generation -----------------------------------------------------------------------------

    private PartnerStatementResponse generateAndIssue(HostOrganization organization, LocalDate periodStart,
            LocalDate periodEnd, UUID actorUserId, String actorType) {
        UUID organizationId = organization.getId();
        String currency = organization.getDefaultCurrency();
        // The miss-then-insert is a race: two admins generating the same new period both read
        // nothing and both insert, and uq_partner_statement_period lets only one of them win.
        // The loser must join the winner's statement and regenerate it, not fail with a 500, so
        // it re-reads the period and carries on into the lock below.
        PartnerStatement statement = findOrCreateStatement(organizationId, periodStart, periodEnd, currency);
        // Everything that rewrites a statement queues behind its row: two generators for the same
        // period would otherwise each delete and rebuild the other's lines. Re-read afterwards,
        // because whoever held the lock first may have settled or disputed it in the meantime.
        finance.lockStatement(statement.getId());
        statement = finance.findStatementById(statement.getId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Statement not found"));
        if (PartnerStatementStatus.SETTLED.name().equals(statement.getStatus())) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "This period is already settled and can no longer be regenerated");
        }

        // Lines a partner already contested survive a regeneration; everything else is rebuilt.
        finance.deleteUndisputedLines(statement.getId());
        Set<UUID> preserved = new HashSet<>();
        for (PartnerStatementLine kept : finance.findLines(statement.getId())) {
            preserved.add(kept.getHotelBookingId() == null ? kept.getActivityOrderId() : kept.getHotelBookingId());
        }

        BigDecimal defaultPercent = commissionService.currentCommissionPercent(organizationId);
        LocalDateTime now = LocalDateTime.now();
        for (LineDraft draft : billableDrafts(organizationId, periodStart, periodEnd, statement.getId())) {
            if (preserved.contains(draft.bookingId())) continue;
            BigDecimal percent = draft.commissionPercent();
            if (percent == null) {
                // Never stamped (booking predates the wiring, or the stamp failed): freeze it now so
                // the statement charges the rate that is on the row, not a rate read at billing time.
                commissionService.stampCommission(draft.bookingType(), draft.bookingId());
                percent = defaultPercent;
            }
            BigDecimal commission = commissionOf(draft.gross(), percent);
            boolean hotel = MarketplaceBookingType.HOTEL.name().equals(draft.bookingType());
            finance.insertLine(PartnerStatementLine.builder()
                    .id(UUID.randomUUID()).statementId(statement.getId()).bookingType(draft.bookingType())
                    .hotelBookingId(hotel ? draft.bookingId() : null)
                    .activityOrderId(hotel ? null : draft.bookingId())
                    .bookingCode(draft.bookingCode()).guestName(draft.guestName()).serviceDate(draft.serviceDate())
                    .grossAmount(scaled(draft.gross())).commissionPercent(percent).commissionAmount(commission)
                    .netAmount(scaled(draft.gross().subtract(commission))).lineReason(draft.reason().name())
                    .disputeStatus(StatementDisputeStatus.NONE.name()).createdAt(now)
                    .build());
        }

        PartnerStatementStatus status = recompute(statement, currency);
        boolean firstIssue = statement.getIssuedAt() == null;
        boolean hasActivity = statement.getBookingCount() != null && statement.getBookingCount() > 0;
        if (status != PartnerStatementStatus.DISPUTED) status = PartnerStatementStatus.ISSUED;
        finance.updateStatementStatus(statement.getId(), status.name(), firstIssue ? now : null, null, null, now);
        history.record(organizationId, "PARTNER_STATEMENT", statement.getId(),
                firstIssue ? "ISSUED" : "REGENERATED", statement, List.of("status", "grossAmount"),
                actorUserId, actorType, null);

        PartnerStatement issued = finance.findStatementById(statement.getId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Statement not found"));
        // A month with no billable booking still gets a statement (the zero is auditable), but nobody
        // is told about it: the job runs for every organization, most of which owe nothing.
        if (firstIssue && hasActivity) notifyStatementReady(organization, issued, actorUserId);
        return toResponse(issued, finance.findLines(issued.getId()));
    }

    /**
     * The statement for a period, creating it when this is its first generation.
     *
     * <p>The insert is the conditional one in the mapper ({@code ON CONFLICT ... DO NOTHING} on
     * uq_partner_statement_period), so a concurrent first generation of the same period does not
     * raise a duplicate key: the loser's insert waits for the winner, writes nothing, and the
     * re-read below hands back the winner's row. The catch covers the same race on a database or
     * a mapper where that conflict clause is not in force -- the loser re-reads and joins the
     * winner's statement instead of surfacing a 500 -- and only re-throws when no statement turns
     * up, which is a real failure rather than a race.
     */
    private PartnerStatement findOrCreateStatement(UUID organizationId, LocalDate periodStart,
            LocalDate periodEnd, String currency) {
        Optional<PartnerStatement> existing = finance.findStatementByPeriod(organizationId, periodStart, periodEnd);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return createStatement(organizationId, periodStart, periodEnd, currency);
        } catch (DataIntegrityViolationException e) {
            return finance.findStatementByPeriod(organizationId, periodStart, periodEnd)
                    .orElseThrow(() -> e);
        }
    }

    private PartnerStatement createStatement(UUID organizationId, LocalDate periodStart, LocalDate periodEnd, String currency) {
        LocalDateTime now = LocalDateTime.now();
        finance.insertStatement(PartnerStatement.builder()
                .id(UUID.randomUUID()).organizationId(organizationId).periodStart(periodStart).periodEnd(periodEnd)
                .currency(currency).grossAmount(BigDecimal.ZERO).commissionAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO).bookingCount(0).status(PartnerStatementStatus.OPEN.name())
                .dataVersion(1L).createdAt(now).updatedAt(now).build());
        // Re-read rather than trust the local object: a concurrent generator may have won the insert.
        return finance.findStatementByPeriod(organizationId, periodStart, periodEnd)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Statement could not be created"));
    }

    /**
     * Rewrites the statement totals as the sum of its surviving lines and returns the status those
     * lines imply. Called after generation and after every dispute resolution, so a total is never
     * adjusted by hand.
     */
    private PartnerStatementStatus recompute(PartnerStatement statement, String currency) {
        List<PartnerStatementLine> lines = finance.findLines(statement.getId());
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        boolean anyOpen = false;
        for (PartnerStatementLine line : lines) {
            gross = gross.add(nullToZero(line.getGrossAmount()));
            commission = commission.add(nullToZero(line.getCommissionAmount()));
            net = net.add(nullToZero(line.getNetAmount()));
            anyOpen |= StatementDisputeStatus.OPEN.name().equals(line.getDisputeStatus());
        }
        statement.setGrossAmount(scaled(gross));
        statement.setCommissionAmount(scaled(commission));
        statement.setNetAmount(scaled(net));
        statement.setBookingCount(lines.size());
        statement.setCurrency(currency == null ? statement.getCurrency() : currency);
        statement.setUpdatedAt(LocalDateTime.now());
        finance.updateStatementTotals(statement);

        PartnerStatementStatus status = anyOpen ? PartnerStatementStatus.DISPUTED
                : statement.getIssuedAt() == null ? PartnerStatementStatus.OPEN : PartnerStatementStatus.ISSUED;
        if (PartnerStatementStatus.SETTLED.name().equals(statement.getStatus())) {
            status = PartnerStatementStatus.SETTLED;
        }
        if (!status.name().equals(statement.getStatus())) {
            finance.updateStatementStatus(statement.getId(), status.name(), null, null, null, statement.getUpdatedAt());
            statement.setStatus(status.name());
        }
        return status;
    }

    /**
     * @param excludeStatementId the statement being built, so its own lines do not exclude their
     *                           own bookings; null for the partner's preview of a period, which
     *                           bills nothing and therefore filters nothing
     */
    private List<LineDraft> billableDrafts(UUID organizationId, LocalDate periodStart, LocalDate periodEnd,
            UUID excludeStatementId) {
        List<LineDraft> drafts = new ArrayList<>();
        List<BillableBookingRow> candidates = new ArrayList<>(
                finance.findHotelBillableCandidates(organizationId, periodStart, periodEnd, excludeStatementId));
        candidates.addAll(finance.findActivityBillableCandidates(organizationId, periodStart, periodEnd, excludeStatementId));
        for (BillableBookingRow row : candidates) {
            billableLine(row).ifPresent(drafts::add);
        }
        return drafts;
    }

    /**
     * The billable rule, in one place.
     *
     * <p>Only three things earn the platform a commission: a stay/visit that happened, a no-show the
     * partner charged the guest for, and a cancellation that carried a penalty. Everything else —
     * a free cancellation, an expired hold, a no-show the guest was not charged for, a zero total —
     * is not billable, and an unparseable penalty is skipped rather than guessed at.
     */
    static Optional<LineDraft> billableLine(BillableBookingRow row) {
        if (row == null || row.getBookingStatus() == null) return Optional.empty();
        String status = row.getBookingStatus();
        BigDecimal total = nullToZero(row.getTotalAmount());
        PartnerStatementLineReason reason;
        BigDecimal gross;
        if ("COMPLETED".equals(status)) {
            reason = PartnerStatementLineReason.COMPLETED;
            gross = total;
        } else if ("NO_SHOW".equals(status)) {
            if (!Boolean.TRUE.equals(row.getGuestCharged())) return Optional.empty();
            reason = PartnerStatementLineReason.NO_SHOW_CHARGED;
            gross = total;
        } else if (status.startsWith("CANCELLED_")) {
            BigDecimal penalty = parsePenalty(row.getCancellationReason());
            if (penalty == null) {
                if (row.getCancellationReason() != null
                        && row.getCancellationReason().toLowerCase(Locale.ROOT).contains("penalty")) {
                    log.warn("Skipping booking {}: a penalty was recorded but could not be parsed from '{}'",
                            row.getBookingId(), row.getCancellationReason());
                }
                return Optional.empty();
            }
            reason = PartnerStatementLineReason.CANCELLATION_FEE;
            gross = penalty;
        } else {
            return Optional.empty();
        }
        if (gross.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();
        return Optional.of(new LineDraft(row.getBookingId(), row.getBookingType(), row.getBookingCode(),
                row.getGuestName(), row.getServiceDate(), reason, scaled(gross), row.getCommissionPercent()));
    }

    /** Pulls the amount out of "… — penalty 450000.00 VND (PERCENT_50)"; null when there is none. */
    static BigDecimal parsePenalty(String cancellationReason) {
        if (cancellationReason == null) return null;
        Matcher matcher = PENALTY_AMOUNT.matcher(cancellationReason);
        if (!matcher.find()) return null;
        try {
            return new BigDecimal(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** A billable booking before it becomes a persisted statement line. */
    record LineDraft(UUID bookingId, String bookingType, String bookingCode, String guestName,
                     LocalDate serviceDate, PartnerStatementLineReason reason, BigDecimal gross,
                     BigDecimal commissionPercent) {
    }

    // --- Notification ---------------------------------------------------------------------------

    private void notifyStatementReady(HostOrganization organization, PartnerStatement statement, UUID actorUserId) {
        Map<String, Object> data = new HashMap<>();
        data.put("statementId", statement.getId().toString());
        data.put("organizationId", organization.getId().toString());
        data.put("periodStart", statement.getPeriodStart().toString());
        data.put("periodEnd", statement.getPeriodEnd().toString());
        data.put("periodLabel", periodLabel(statement));
        data.put("netAmount", formatMoney(statement.getNetAmount(), statement.getCurrency()));
        data.put("deepLink", "/partner/finance");
        for (UUID recipient : statementRecipients(organization)) {
            try {
                String language = users.findById(recipient).map(User::getLanguage).orElse(null);
                NotificationMessage message = templateRenderer.render(NotificationType.PARTNER_STATEMENT_READY, data, language);
                notificationService.createNotification(recipient, null, NotificationType.PARTNER_STATEMENT_READY,
                        message.title(), message.body(), data, actorUserId);
            } catch (RuntimeException ex) {
                // The statement is already issued; a failed notification must not undo it.
                log.warn("Could not notify {} about statement {}: {}", recipient, statement.getId(), ex.getMessage());
            }
        }
    }

    /**
     * The owner plus every active {@code FINANCE} / {@code PARTNER_ADMIN} member. The owner is never
     * an {@code organization_members} row, so it has to be added explicitly.
     */
    private List<UUID> statementRecipients(HostOrganization organization) {
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        if (organization.getOwnerUserId() != null) recipients.add(organization.getOwnerUserId());
        for (OrganizationMember member : organizations.findMembers(organization.getId())) {
            if (!OrganizationMemberStatus.ACTIVE.name().equals(member.getMemberStatus())) continue;
            if (STATEMENT_RECIPIENT_ROLES.contains(member.getRoleCode())) recipients.add(member.getUserId());
        }
        return List.copyOf(recipients);
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** Finance is readable with either the reporting permission or plain organization access. */
    private HostOrganization requireFinanceRead(UUID organizationId, UUID actorUserId) {
        if (authorization.hasPermission(organizationId, actorUserId, "REPORT_READ")) {
            return authorization.requirePermission(organizationId, actorUserId, "REPORT_READ");
        }
        return authorization.requirePermission(organizationId, actorUserId, "ORGANIZATION_READ");
    }

    private HostOrganization organizationOf(UUID organizationId) {
        return organizations.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Partner organization not found"));
    }

    /** A guessed statement UUID from another organization must read as "not found", not as access. */
    private PartnerStatement statementOf(UUID organizationId, UUID statementId) {
        PartnerStatement statement = finance.findStatementById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Statement not found"));
        if (!statement.getOrganizationId().equals(organizationId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Statement not found");
        }
        return statement;
    }

    private PartnerStatementLine lineOf(UUID statementId, UUID lineId) {
        PartnerStatementLine line = finance.findLine(lineId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Statement line not found"));
        if (!line.getStatementId().equals(statementId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Statement line not found");
        }
        return line;
    }

    private PartnerStatementResponse partnerGetInternal(UUID organizationId, UUID statementId) {
        return toResponse(statementOf(organizationId, statementId), finance.findLines(statementId));
    }

    private int disputeWindowDays() {
        return businessConfig.getInt(BusinessConfigKey.MARKETPLACE_STATEMENT_DISPUTE_WINDOW_DAYS);
    }

    private LocalDateTime disputeWindowEnd(PartnerStatement statement) {
        return statement.getIssuedAt() == null ? null : statement.getIssuedAt().plusDays(disputeWindowDays());
    }

    private LocalDate todayIn(HostOrganization organization) {
        try { return LocalDate.now(ZoneId.of(organization.getTimezone())); }
        catch (RuntimeException ex) { return LocalDate.now(); }
    }

    private static BigDecimal commissionOf(BigDecimal gross, BigDecimal percent) {
        BigDecimal rate = percent == null ? BigDecimal.ZERO : percent;
        return scaled(nullToZero(gross).multiply(rate).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    private static BigDecimal scaled(BigDecimal value) {
        return nullToZero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static String periodLabel(PartnerStatement statement) {
        return statement.getPeriodStart() == null ? "" : PERIOD_LABEL.format(statement.getPeriodStart());
    }

    private static String formatMoney(BigDecimal amount, String currency) {
        return String.format(Locale.US, "%,.2f", nullToZero(amount)) + (currency == null ? "" : " " + currency);
    }

    private static String csvCell(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0) return text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private PartnerStatementResponse toResponse(PartnerStatement statement, List<PartnerStatementLine> lines) {
        LocalDateTime windowEnd = disputeWindowEnd(statement);
        boolean windowOpen = windowEnd != null && !LocalDateTime.now().isAfter(windowEnd)
                && !PartnerStatementStatus.SETTLED.name().equals(statement.getStatus());
        int openDisputes = lines == null ? 0 : (int) lines.stream()
                .filter(line -> StatementDisputeStatus.OPEN.name().equals(line.getDisputeStatus())).count();
        return PartnerStatementResponse.builder()
                .id(statement.getId()).organizationId(statement.getOrganizationId())
                .periodStart(statement.getPeriodStart()).periodEnd(statement.getPeriodEnd())
                .periodLabel(periodLabel(statement)).currency(statement.getCurrency())
                .grossAmount(statement.getGrossAmount()).commissionAmount(statement.getCommissionAmount())
                .netAmount(statement.getNetAmount()).bookingCount(statement.getBookingCount())
                .status(statement.getStatus()).issuedAt(statement.getIssuedAt()).settledAt(statement.getSettledAt())
                .note(statement.getNote()).openDisputes(lines == null ? null : openDisputes)
                .disputeWindowEndsAt(windowEnd).dataVersion(statement.getDataVersion())
                .lines(lines == null ? null : lines.stream().map(line -> toLineResponse(line, windowOpen)).toList())
                .createdAt(statement.getCreatedAt()).updatedAt(statement.getUpdatedAt())
                .build();
    }

    private PartnerStatementLineResponse toLineResponse(PartnerStatementLine line, boolean windowOpen) {
        return PartnerStatementLineResponse.builder()
                .id(line.getId()).statementId(line.getStatementId()).bookingType(line.getBookingType())
                .hotelBookingId(line.getHotelBookingId()).activityOrderId(line.getActivityOrderId())
                .bookingCode(line.getBookingCode()).guestName(line.getGuestName()).serviceDate(line.getServiceDate())
                .grossAmount(line.getGrossAmount()).commissionPercent(line.getCommissionPercent())
                .commissionAmount(line.getCommissionAmount()).netAmount(line.getNetAmount())
                .lineReason(line.getLineReason()).disputeStatus(line.getDisputeStatus())
                .disputeReason(line.getDisputeReason()).disputeOpenedAt(line.getDisputeOpenedAt())
                .disputeResolvedAt(line.getDisputeResolvedAt()).disputeResolutionNote(line.getDisputeResolutionNote())
                .disputable(windowOpen && StatementDisputeStatus.NONE.name().equals(line.getDisputeStatus()))
                .createdAt(line.getCreatedAt())
                .build();
    }
}

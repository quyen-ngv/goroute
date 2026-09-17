package com.ds.goroute.service.impl;

import com.ds.goroute.entity.BillableBookingRow;
import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.PartnerStatement;
import com.ds.goroute.entity.PartnerStatementLine;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.PartnerFinanceRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.MarketplaceCommissionService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerStatementServiceImplTest {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 8, 31);

    private final PartnerFinanceRepository finance = mock(PartnerFinanceRepository.class);
    private final HostOrganizationRepository organizations = mock(HostOrganizationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PartnerAuthorizationService authorization = mock(PartnerAuthorizationService.class);
    private final MarketplaceCommissionService commission = mock(MarketplaceCommissionService.class);
    private final MarketplaceHistoryService history = mock(MarketplaceHistoryService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final NotificationTemplateRenderer renderer = mock(NotificationTemplateRenderer.class);
    private final BusinessConfigService businessConfig = mock(BusinessConfigService.class);

    private final List<PartnerStatementLine> storedLines = new ArrayList<>();

    private PartnerStatementServiceImpl service() {
        when(businessConfig.getInt(BusinessConfigKey.MARKETPLACE_STATEMENT_DISPUTE_WINDOW_DAYS)).thenReturn(14);
        when(commission.currentCommissionPercent(ORGANIZATION_ID)).thenReturn(new BigDecimal("15.00"));
        when(renderer.render(any(), any(), any())).thenReturn(new NotificationMessage("t", "b"));
        when(organizations.findMembers(any())).thenReturn(List.of());
        when(users.findById(any())).thenReturn(Optional.empty());
        when(finance.findLines(any())).thenReturn(storedLines);
        when(finance.insertLine(any())).thenAnswer(invocation -> {
            storedLines.add(invocation.getArgument(0));
            return 1;
        });
        return new PartnerStatementServiceImpl(finance, organizations, users, authorization, commission, history,
                notifications, renderer, businessConfig);
    }

    // --- Billable line selection -----------------------------------------------------------------

    @Test
    void onlyCompletedChargedNoShowsAndPenaltyCancellationsAreBilled() {
        PartnerStatementServiceImpl service = service();
        HostOrganization organization = organization();
        when(organizations.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        PartnerStatement statement = statement(null, "OPEN");
        when(finance.findStatementByPeriod(ORGANIZATION_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.empty(), Optional.of(statement));
        when(finance.findStatementById(statement.getId())).thenReturn(Optional.of(statement));
        when(finance.findHotelBillableCandidates(ORGANIZATION_ID, PERIOD_START, PERIOD_END, statement.getId())).thenReturn(List.of(
                row("HB-COMPLETED", "COMPLETED", "1000000", null, null),
                row("HB-NOSHOW-CHARGED", "NO_SHOW", "800000", Boolean.TRUE, null),
                row("HB-NOSHOW-FREE", "NO_SHOW", "800000", Boolean.FALSE, null),
                row("HB-NOSHOW-UNKNOWN", "NO_SHOW", "800000", null, null),
                row("HB-PENALTY", "CANCELLED_BY_GUEST", "600000", null,
                        "Changed plans — penalty 300000.00 VND (PERCENT_50)"),
                row("HB-FREE-CANCEL", "CANCELLED_BY_GUEST", "600000", null, "Changed plans — free cancellation"),
                row("HB-UNPARSEABLE", "CANCELLED_BY_HOST", "600000", null, "penalty applied, amount to be agreed"),
                row("HB-ZERO", "COMPLETED", "0", null, null)));
        when(finance.findActivityBillableCandidates(ORGANIZATION_ID, PERIOD_START, PERIOD_END, statement.getId())).thenReturn(List.of());

        service.adminGenerate(ACTOR_ID, ORGANIZATION_ID, PERIOD_START, PERIOD_END);

        assertEquals(List.of("HB-COMPLETED", "HB-NOSHOW-CHARGED", "HB-PENALTY"),
                storedLines.stream().map(PartnerStatementLine::getBookingCode).toList());
        assertEquals(List.of("COMPLETED", "NO_SHOW_CHARGED", "CANCELLATION_FEE"),
                storedLines.stream().map(PartnerStatementLine::getLineReason).toList());
        // A cancellation is billed on the penalty, not on the booking total.
        assertEquals(0, new BigDecimal("300000.00").compareTo(storedLines.get(2).getGrossAmount()));
        // 15% of 1 000 000, and the partner keeps the rest.
        assertEquals(0, new BigDecimal("150000.00").compareTo(storedLines.get(0).getCommissionAmount()));
        assertEquals(0, new BigDecimal("850000.00").compareTo(storedLines.get(0).getNetAmount()));
    }

    @Test
    void aBookingThatWasNeverStampedIsStampedBeforeItIsBilled() {
        PartnerStatementServiceImpl service = service();
        when(organizations.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization()));
        PartnerStatement statement = statement(null, "OPEN");
        when(finance.findStatementByPeriod(ORGANIZATION_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.empty(), Optional.of(statement));
        when(finance.findStatementById(statement.getId())).thenReturn(Optional.of(statement));
        BillableBookingRow stamped = row("HB-STAMPED", "COMPLETED", "1000000", null, null);
        stamped.setCommissionPercent(new BigDecimal("10.00"));
        BillableBookingRow unstamped = row("HB-UNSTAMPED", "COMPLETED", "1000000", null, null);
        when(finance.findHotelBillableCandidates(ORGANIZATION_ID, PERIOD_START, PERIOD_END, statement.getId()))
                .thenReturn(List.of(stamped, unstamped));
        when(finance.findActivityBillableCandidates(ORGANIZATION_ID, PERIOD_START, PERIOD_END, statement.getId())).thenReturn(List.of());

        service.adminGenerate(ACTOR_ID, ORGANIZATION_ID, PERIOD_START, PERIOD_END);

        verify(commission).stampCommission("HOTEL", unstamped.getBookingId());
        verify(commission, never()).stampCommission("HOTEL", stamped.getBookingId());
        // The frozen rate wins over the organization's current one.
        assertEquals(0, new BigDecimal("100000.00").compareTo(storedLines.get(0).getCommissionAmount()));
        assertEquals(0, new BigDecimal("150000.00").compareTo(storedLines.get(1).getCommissionAmount()));
    }

    // --- Dispute window --------------------------------------------------------------------------

    @Test
    void aDisputeIsAcceptedInsideTheFourteenDayWindow() {
        PartnerStatementServiceImpl service = service();
        PartnerStatement statement = issuedStatement(LocalDateTime.now().minusDays(3));
        PartnerStatementLine line = line(statement.getId(), "NONE", "1000000", "150000");
        storedLines.add(line);
        allowFinanceRead();
        when(finance.findStatementById(statement.getId())).thenReturn(Optional.of(statement));
        when(finance.findLine(line.getId())).thenReturn(Optional.of(line));
        when(finance.openDispute(eq(line.getId()), anyString(), any())).thenReturn(1);

        service.partnerOpenDispute(ACTOR_ID, ORGANIZATION_ID, statement.getId(), line.getId(), "Guest never arrived");

        verify(finance).openDispute(eq(line.getId()), eq("Guest never arrived"), any());
        verify(finance).updateStatementStatus(eq(statement.getId()), eq("DISPUTED"), eq(null), eq(null), eq(null), any());
    }

    @Test
    void aDisputeIsRefusedOnceTheFourteenDayWindowClosed() {
        PartnerStatementServiceImpl service = service();
        PartnerStatement statement = issuedStatement(LocalDateTime.now().minusDays(15));
        PartnerStatementLine line = line(statement.getId(), "NONE", "1000000", "150000");
        storedLines.add(line);
        allowFinanceRead();
        when(finance.findStatementById(statement.getId())).thenReturn(Optional.of(statement));
        when(finance.findLine(line.getId())).thenReturn(Optional.of(line));

        assertThrows(BusinessException.class, () -> service.partnerOpenDispute(ACTOR_ID, ORGANIZATION_ID,
                statement.getId(), line.getId(), "Too late"));
        verify(finance, never()).openDispute(any(), anyString(), any());
    }

    @Test
    void aStatementThatWasNeverIssuedCannotBeDisputed() {
        PartnerStatementServiceImpl service = service();
        PartnerStatement statement = statement(null, "OPEN");
        allowFinanceRead();
        when(finance.findStatementById(statement.getId())).thenReturn(Optional.of(statement));

        assertThrows(BusinessException.class, () -> service.partnerOpenDispute(ACTOR_ID, ORGANIZATION_ID,
                statement.getId(), UUID.randomUUID(), "Not issued yet"));
        verify(finance, never()).openDispute(any(), anyString(), any());
    }

    // --- Recomputation after a dispute is accepted ------------------------------------------------

    @Test
    void acceptingADisputeZeroesTheLineAndRecomputesTheStatement() {
        PartnerStatementServiceImpl service = service();
        PartnerStatement statement = issuedStatement(LocalDateTime.now().minusDays(2));
        statement.setStatus("DISPUTED");
        PartnerStatementLine clean = line(statement.getId(), "NONE", "1000000", "150000");
        PartnerStatementLine disputed = line(statement.getId(), "OPEN", "600000", "90000");
        storedLines.add(clean);
        storedLines.add(disputed);
        when(finance.findStatementById(statement.getId())).thenReturn(Optional.of(statement));
        when(finance.findLine(disputed.getId())).thenReturn(Optional.of(disputed));
        when(finance.resolveDispute(eq(disputed.getId()), eq("ACCEPTED"), any(), eq(ACTOR_ID), any(), eq(true)))
                .thenAnswer(invocation -> {
                    disputed.setDisputeStatus("ACCEPTED");
                    disputed.setCommissionAmount(BigDecimal.ZERO);
                    disputed.setNetAmount(disputed.getGrossAmount());
                    return 1;
                });

        service.adminResolveDispute(ACTOR_ID, statement.getId(), disputed.getId(), true, "Guest was charged twice");

        ArgumentCaptor<PartnerStatement> saved = ArgumentCaptor.forClass(PartnerStatement.class);
        verify(finance).updateStatementTotals(saved.capture());
        PartnerStatement totals = saved.getValue();
        assertEquals(0, new BigDecimal("1600000.00").compareTo(totals.getGrossAmount()));
        // Only the surviving line still carries commission.
        assertEquals(0, new BigDecimal("150000.00").compareTo(totals.getCommissionAmount()));
        assertEquals(0, new BigDecimal("1450000.00").compareTo(totals.getNetAmount()));
        assertEquals(2, totals.getBookingCount());
        // No open dispute is left, so the statement goes back to ISSUED.
        verify(finance).updateStatementStatus(eq(statement.getId()), eq("ISSUED"), eq(null), eq(null), eq(null), any());
    }

    @Test
    void rejectingADisputeRequiresANote() {
        PartnerStatementServiceImpl service = service();
        PartnerStatement statement = issuedStatement(LocalDateTime.now().minusDays(2));
        PartnerStatementLine disputed = line(statement.getId(), "OPEN", "600000", "90000");
        storedLines.add(disputed);
        when(finance.findStatementById(statement.getId())).thenReturn(Optional.of(statement));
        when(finance.findLine(disputed.getId())).thenReturn(Optional.of(disputed));

        assertThrows(BusinessException.class,
                () -> service.adminResolveDispute(ACTOR_ID, statement.getId(), disputed.getId(), false, "  "));
        verify(finance, never()).resolveDispute(any(), anyString(), any(), any(), any(), eq(false));
    }

    @Test
    void aStatementFromAnotherOrganizationReadsAsNotFound() {
        PartnerStatementServiceImpl service = service();
        PartnerStatement foreign = statement(null, "ISSUED");
        foreign.setOrganizationId(UUID.randomUUID());
        allowFinanceRead();
        when(finance.findStatementById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(BusinessException.class,
                () -> service.partnerGet(ACTOR_ID, ORGANIZATION_ID, foreign.getId()));
    }

    @Test
    void penaltyParsingHandlesTheRecordedCancellationSentence() {
        assertEquals(0, new BigDecimal("450000.00").compareTo(PartnerStatementServiceImpl.parsePenalty(
                "Changed plans — penalty 450000.00 VND (PERCENT_50)")));
        assertTrue(PartnerStatementServiceImpl.parsePenalty("Changed plans — free cancellation") == null);
        assertTrue(PartnerStatementServiceImpl.parsePenalty(null) == null);
    }

    // --- Fixtures --------------------------------------------------------------------------------

    private void allowFinanceRead() {
        HostOrganization organization = organization();
        when(authorization.hasPermission(ORGANIZATION_ID, ACTOR_ID, "REPORT_READ")).thenReturn(true);
        when(authorization.requirePermission(ORGANIZATION_ID, ACTOR_ID, "REPORT_READ")).thenReturn(organization);
        when(organizations.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
    }

    private HostOrganization organization() {
        return HostOrganization.builder().id(ORGANIZATION_ID).ownerUserId(UUID.randomUUID())
                .legalName("Legal").displayName("Display").organizationType("BUSINESS")
                .verificationStatus("VERIFIED").operationalStatus("ENABLED").defaultCurrency("VND")
                .timezone("Asia/Ho_Chi_Minh").settings("{}").commissionPercent(new BigDecimal("15.00"))
                .dataVersion(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private PartnerStatement statement(LocalDateTime issuedAt, String status) {
        return PartnerStatement.builder().id(UUID.randomUUID()).organizationId(ORGANIZATION_ID)
                .periodStart(PERIOD_START).periodEnd(PERIOD_END).currency("VND")
                .grossAmount(BigDecimal.ZERO).commissionAmount(BigDecimal.ZERO).netAmount(BigDecimal.ZERO)
                .bookingCount(0).status(status).issuedAt(issuedAt).dataVersion(1L)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private PartnerStatement issuedStatement(LocalDateTime issuedAt) {
        return statement(issuedAt, "ISSUED");
    }

    private PartnerStatementLine line(UUID statementId, String disputeStatus, String gross, String commissionAmount) {
        BigDecimal grossAmount = new BigDecimal(gross);
        BigDecimal commissionValue = new BigDecimal(commissionAmount);
        return PartnerStatementLine.builder().id(UUID.randomUUID()).statementId(statementId).bookingType("HOTEL")
                .hotelBookingId(UUID.randomUUID()).bookingCode("HB-1").guestName("Guest")
                .serviceDate(PERIOD_START.plusDays(3)).grossAmount(grossAmount)
                .commissionPercent(new BigDecimal("15.00")).commissionAmount(commissionValue)
                .netAmount(grossAmount.subtract(commissionValue)).lineReason("COMPLETED")
                .disputeStatus(disputeStatus).createdAt(LocalDateTime.now()).build();
    }

    private BillableBookingRow row(String code, String status, String total, Boolean guestCharged, String cancellationReason) {
        return BillableBookingRow.builder().bookingId(UUID.randomUUID()).bookingType("HOTEL").bookingCode(code)
                .guestName("Guest").serviceDate(PERIOD_START.plusDays(5)).currency("VND")
                .totalAmount(new BigDecimal(total)).bookingStatus(status).guestCharged(guestCharged)
                .cancellationReason(cancellationReason).build();
    }
}

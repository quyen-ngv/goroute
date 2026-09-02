package com.ds.goroute.service;

import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.PartnerFinanceSummaryResponse;
import com.ds.goroute.dto.response.PartnerStatementResponse;
import com.ds.goroute.type.PartnerStatementStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Monthly reconciliation between the platform and a partner organization.
 *
 * <p>Everything here is bookkeeping: a statement records what commission is owed for a period and
 * lets the partner contest individual lines. No money is charged, refunded or paid out — there is
 * no payment gateway in the product yet.
 */
public interface PartnerStatementService {

    // --- Partner console ------------------------------------------------------------------------

    PageResponse<PartnerStatementResponse> partnerList(UUID actorUserId, UUID organizationId, int page, int size);

    PartnerStatementResponse partnerGet(UUID actorUserId, UUID organizationId, UUID statementId);

    /** RFC 4180 CSV, one row per statement line. */
    String partnerExportCsv(UUID actorUserId, UUID organizationId, UUID statementId);

    PartnerStatementResponse partnerOpenDispute(UUID actorUserId, UUID organizationId, UUID statementId,
                                                UUID lineId, String reason);

    /** Accrual for the month in progress plus the last issued statement. Nothing is persisted. */
    PartnerFinanceSummaryResponse partnerSummary(UUID actorUserId, UUID organizationId);

    // --- Admin ----------------------------------------------------------------------------------

    PageResponse<PartnerStatementResponse> adminList(UUID organizationId, int page, int size);

    PartnerStatementResponse adminGet(UUID organizationId, UUID statementId);

    /** Generates or refreshes the period and issues it (notifying the partner on first issue). */
    PartnerStatementResponse adminGenerate(UUID actorUserId, UUID organizationId,
                                           LocalDate periodStart, LocalDate periodEnd);

    PartnerStatementResponse adminResolveDispute(UUID actorUserId, UUID statementId, UUID lineId,
                                                 boolean accept, String note);

    PartnerStatementResponse adminUpdateStatus(UUID actorUserId, UUID statementId,
                                               PartnerStatementStatus status, String note);

    // --- Job ------------------------------------------------------------------------------------

    /** Issues the calendar month before {@code today} in the organization's own timezone. */
    PartnerStatementResponse issuePreviousMonth(UUID organizationId);
}

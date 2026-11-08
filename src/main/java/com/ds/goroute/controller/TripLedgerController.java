package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.LedgerSettingsRequest;
import com.ds.goroute.dto.response.SettlementPageResponse;
import com.ds.goroute.dto.response.TripLedgerResponse;
import com.ds.goroute.service.expense.LedgerService;
import com.ds.goroute.service.expense.SettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A trip's balances and payments (epic 05 §2.4f); owner and accepted members only. */
@RestController
@Validated
@RequestMapping("/v1/api/trips/{tripId}")
@RequiredArgsConstructor
public class TripLedgerController extends BaseController {

    private final LedgerService ledgerService;
    private final SettlementService settlementService;

    @GetMapping("/ledger")
    public ResponseEntity<BaseResponse<TripLedgerResponse>> getLedger(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(ledgerService.tripLedger(tripId, userId)));
    }

    /** Owner or editor: whether the trip suggests the fewest transfers or each person's own debts. */
    @PatchMapping("/ledger/settings")
    public ResponseEntity<BaseResponse<TripLedgerResponse>> updateSettings(
            @PathVariable UUID tripId,
            @Valid @RequestBody LedgerSettingsRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(
                ledgerService.updateSettings(tripId, request.getSimplifyDebts(), userId)));
    }

    /** Newest payment first; deleted ones only when asked, for the undo history. */
    @GetMapping("/settlements")
    public ResponseEntity<BaseResponse<SettlementPageResponse>> getSettlements(
            @PathVariable UUID tripId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(
                settlementService.tripPage(tripId, page, size, includeDeleted, userId)));
    }
}

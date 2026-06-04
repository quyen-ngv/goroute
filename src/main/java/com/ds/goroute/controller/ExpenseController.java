package com.ds.goroute.controller;

import com.ds.goroute.dto.request.CreateExpenseRequest;
import com.ds.goroute.dto.request.UpdateExpenseRequest;
import com.ds.goroute.dto.request.MarkPaymentRequest;
import com.ds.goroute.dto.response.BudgetOverviewResponse;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.ExpenseSplitResponse;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.BaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/trips/{tripId}/expenses")
@RequiredArgsConstructor
@Slf4j
public class ExpenseController extends BaseService {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<ExpenseResponse>>> getExpenses(
            @PathVariable UUID tripId,
            @RequestParam(required = false) String category,
            @RequestAttribute("userId") UUID userId) {
        List<ExpenseResponse> expenses = expenseService.getExpenses(tripId, category);
        return ResponseEntity.ok(ofSucceeded(expenses));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ExpenseResponse>> createExpense(
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateExpenseRequest request,
            @RequestAttribute("userId") UUID userId) {
        ExpenseResponse expense = expenseService.createExpense(tripId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(expense));
    }

    @GetMapping("/overview")
    public ResponseEntity<BaseResponse<BudgetOverviewResponse>> getBudgetOverview(
            @PathVariable UUID tripId,
            @RequestAttribute("userId") UUID userId) {
        BudgetOverviewResponse overview = expenseService.getBudgetOverview(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(overview));
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<BaseResponse<ExpenseResponse>> updateExpense(
            @PathVariable UUID tripId,
            @PathVariable UUID expenseId,
            @Valid @RequestBody UpdateExpenseRequest request,
            @RequestAttribute("userId") UUID userId) {
        ExpenseResponse expense = expenseService.updateExpense(tripId, expenseId, request, userId);
        return ResponseEntity.ok(ofSucceeded(expense));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<BaseResponse<Void>> deleteExpense(
            @PathVariable UUID tripId,
            @PathVariable UUID expenseId,
            @RequestAttribute("userId") UUID userId) {
        expenseService.deleteExpense(tripId, expenseId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PatchMapping("/{expenseId}/splits/{splitId}/mark-paid")
    public ResponseEntity<BaseResponse<ExpenseSplitResponse>> markPaymentForSplit(
            @PathVariable UUID tripId,
            @PathVariable UUID expenseId,
            @PathVariable UUID splitId,
            @Valid @RequestBody MarkPaymentRequest request,
            @RequestAttribute("userId") UUID userId) {
        log.info("ðŸ”µ markPaymentForSplit endpoint called: tripId={}, expenseId={}, splitId={}, isPaid={}",
                tripId, expenseId, splitId, request.getIsPaid());
        ExpenseSplitResponse split = expenseService.markPaymentForSplit(tripId, expenseId, splitId, request, userId);
        return ResponseEntity.ok(ofSucceeded(split));
    }

    @PatchMapping("/{expenseId}/mark-all-paid")
    public ResponseEntity<BaseResponse<ExpenseResponse>> markAllPaymentsForExpense(
            @PathVariable UUID tripId,
            @PathVariable UUID expenseId,
            @Valid @RequestBody MarkPaymentRequest request,
            @RequestAttribute("userId") UUID userId) {
        ExpenseResponse expense = expenseService.markAllPaymentsForExpense(tripId, expenseId, request, userId);
        return ResponseEntity.ok(ofSucceeded(expense));
    }

    @PatchMapping("/mark-all-paid")
    public ResponseEntity<BaseResponse<Void>> markAllPaymentsForTrip(
            @PathVariable UUID tripId,
            @Valid @RequestBody MarkPaymentRequest request,
            @RequestAttribute("userId") UUID userId) {
        expenseService.markAllPaymentsForTrip(tripId, request, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}

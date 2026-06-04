package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateExpenseRequest;
import com.ds.goroute.dto.request.UpdateExpenseRequest;
import com.ds.goroute.dto.request.MarkPaymentRequest;
import com.ds.goroute.dto.response.BudgetOverviewResponse;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.ExpenseSplitResponse;

import java.util.List;
import java.util.UUID;

public interface ExpenseService {
    ExpenseResponse createExpense(UUID tripId, CreateExpenseRequest request, UUID userId);

    List<ExpenseResponse> getExpenses(UUID tripId, String category);

    BudgetOverviewResponse getBudgetOverview(UUID tripId, UUID userId);

    ExpenseResponse updateExpense(UUID tripId, UUID expenseId, UpdateExpenseRequest request, UUID userId);

    void deleteExpense(UUID tripId, UUID expenseId, UUID userId);

    ExpenseSplitResponse markPaymentForSplit(UUID tripId, UUID expenseId, UUID splitId, MarkPaymentRequest request, UUID userId);

    ExpenseResponse markAllPaymentsForExpense(UUID tripId, UUID expenseId, MarkPaymentRequest request, UUID userId);

    void markAllPaymentsForTrip(UUID tripId, MarkPaymentRequest request, UUID userId);

    /** Re-apply trip currency conversion for all expenses after trip.currency changes. */
    void recalculateForTripCurrency(UUID tripId);
}

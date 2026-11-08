package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateExpenseRequest;
import com.ds.goroute.dto.request.UpdateExpenseRequest;
import com.ds.goroute.dto.request.MarkPaymentRequest;
import com.ds.goroute.dto.response.BudgetOverviewResponse;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.ExpenseSplitResponse;
import com.ds.goroute.type.ExpenseCategory;

import java.util.List;
import java.util.UUID;

public interface ExpenseService {
    ExpenseResponse createExpense(UUID tripId, CreateExpenseRequest request, UUID userId);

    List<ExpenseResponse> getExpenses(UUID tripId, ExpenseCategory category, UUID userId);

    /** One page when {@code page} or {@code size} is set; the whole list otherwise, as before. */
    List<ExpenseResponse> getExpenses(UUID tripId, ExpenseCategory category, Integer page, Integer size, UUID userId);

    BudgetOverviewResponse getBudgetOverview(UUID tripId, UUID userId);

    ExpenseResponse updateExpense(UUID tripId, UUID expenseId, UpdateExpenseRequest request, UUID userId);

    void deleteExpense(UUID tripId, UUID expenseId, UUID userId);

    ExpenseSplitResponse markPaymentForSplit(UUID tripId, UUID expenseId, UUID splitId, MarkPaymentRequest request, UUID userId);

    ExpenseResponse markAllPaymentsForExpense(UUID tripId, UUID expenseId, MarkPaymentRequest request, UUID userId);

    void markAllPaymentsForTrip(UUID tripId, MarkPaymentRequest request, UUID userId);

    /**
     * Re-converts every expense of the trip into its (new) currency at the rate of the day each was
     * spent. Inside a transaction it runs once that transaction has committed, so no database
     * connection is held while rates are fetched.
     */
    void recalculateForTripCurrency(UUID tripId);
}

package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateExpenseRequest;
import com.ds.goroute.dto.request.UpdateExpenseRequest;
import com.ds.goroute.dto.response.BudgetOverviewResponse;
import com.ds.goroute.dto.response.ExpenseResponse;

import java.util.List;
import java.util.UUID;

public interface ExpenseService {
    ExpenseResponse createExpense(UUID tripId, CreateExpenseRequest request, UUID userId);
    
    List<ExpenseResponse> getExpenses(UUID tripId, String category);
    
    BudgetOverviewResponse getBudgetOverview(UUID tripId);
    
    ExpenseResponse updateExpense(UUID tripId, UUID expenseId, UpdateExpenseRequest request, UUID userId);
    
    void deleteExpense(UUID tripId, UUID expenseId, UUID userId);
}

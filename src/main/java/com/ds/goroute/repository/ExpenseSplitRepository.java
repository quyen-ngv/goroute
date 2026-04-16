package com.ds.goroute.repository;

import com.ds.goroute.entity.ExpenseSplit;

import java.util.List;
import java.util.UUID;

public interface ExpenseSplitRepository {
    void save(ExpenseSplit split);
    ExpenseSplit findById(UUID id);
    List<ExpenseSplit> findByExpenseId(UUID expenseId);
    List<ExpenseSplit> findByUserId(UUID userId);
    void update(ExpenseSplit split);
    void delete(UUID id);
    void deleteByExpenseId(UUID expenseId);
}

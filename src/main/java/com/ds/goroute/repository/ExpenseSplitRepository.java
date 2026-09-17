package com.ds.goroute.repository;

import com.ds.goroute.entity.ExpenseSplit;

import java.util.List;
import java.util.UUID;

public interface ExpenseSplitRepository {
    void save(ExpenseSplit split);
    ExpenseSplit findById(UUID id);
    List<ExpenseSplit> findByExpenseId(UUID expenseId);
    /** Batch form of {@link #findByExpenseId}; an empty id list yields an empty list. */
    List<ExpenseSplit> findByExpenseIds(java.util.Collection<UUID> expenseIds);
    List<ExpenseSplit> findByUserId(UUID userId);
    List<ExpenseSplit> findByGuestMemberId(UUID guestMemberId);
    void update(ExpenseSplit split);
    void delete(UUID id);
    void deleteByExpenseId(UUID expenseId);
}

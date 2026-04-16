package com.ds.goroute.repository;

import com.ds.goroute.entity.Expense;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository {
    void insert(Expense expense);
    
    Optional<Expense> findById(UUID id);
    
    List<Expense> findByTripId(UUID tripId);
    
    List<Expense> findByActivityId(UUID activityId);
    
    List<Expense> findByPaidBy(UUID userId);
    
    void updateById(Expense expense);
    
    void deleteById(UUID id);
    
    void deleteByTripId(UUID tripId);
}

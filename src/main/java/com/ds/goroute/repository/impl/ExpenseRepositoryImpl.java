package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Expense;
import com.ds.goroute.mapper.ExpenseMapper;
import com.ds.goroute.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ExpenseRepositoryImpl implements ExpenseRepository {
    
    private final ExpenseMapper expenseMapper;
    
    @Override
    public void insert(Expense expense) {
        expenseMapper.insert(expense);
    }
    
    @Override
    public Optional<Expense> findById(UUID id) {
        return Optional.ofNullable(expenseMapper.selectById(id));
    }
    
    @Override
    public List<Expense> findByTripId(UUID tripId) {
        return expenseMapper.selectByTripId(tripId);
    }
    
    @Override
    public List<Expense> findByActivityId(UUID activityId) {
        return expenseMapper.selectByActivityId(activityId);
    }
    
    @Override
    public List<Expense> findByPaidBy(UUID userId) {
        return expenseMapper.selectByPaidBy(userId);
    }
    
    @Override
    public void updateById(Expense expense) {
        expenseMapper.updateById(expense);
    }
    
    @Override
    public void deleteById(UUID id) {
        expenseMapper.deleteById(id);
    }
    
    @Override
    public void deleteByTripId(UUID tripId) {
        expenseMapper.deleteByTripId(tripId);
    }
}

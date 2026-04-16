package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.mapper.ExpenseSplitMapper;
import com.ds.goroute.repository.ExpenseSplitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ExpenseSplitRepositoryImpl implements ExpenseSplitRepository {
    
    private final ExpenseSplitMapper expenseSplitMapper;

    @Override
    public void save(ExpenseSplit split) {
        expenseSplitMapper.insert(split);
    }

    @Override
    public ExpenseSplit findById(UUID id) {
        return expenseSplitMapper.selectById(id);
    }

    @Override
    public List<ExpenseSplit> findByExpenseId(UUID expenseId) {
        return expenseSplitMapper.selectByExpenseId(expenseId);
    }

    @Override
    public List<ExpenseSplit> findByUserId(UUID userId) {
        return expenseSplitMapper.selectByUserId(userId);
    }

    @Override
    public void update(ExpenseSplit split) {
        expenseSplitMapper.updateById(split);
    }

    @Override
    public void delete(UUID id) {
        expenseSplitMapper.deleteById(id);
    }

    @Override
    public void deleteByExpenseId(UUID expenseId) {
        expenseSplitMapper.deleteByExpenseId(expenseId);
    }
}

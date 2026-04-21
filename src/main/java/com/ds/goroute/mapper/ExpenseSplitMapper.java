package com.ds.goroute.mapper;

import com.ds.goroute.entity.ExpenseSplit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ExpenseSplitMapper {
    int insert(ExpenseSplit split);
    
    ExpenseSplit selectById(@Param("id") UUID id);
    
    List<ExpenseSplit> selectByExpenseId(@Param("expenseId") UUID expenseId);
    
    List<ExpenseSplit> selectByUserId(@Param("userId") UUID userId);
    
    List<ExpenseSplit> selectByGuestMemberId(@Param("guestMemberId") UUID guestMemberId);
    
    int updateById(ExpenseSplit split);
    
    int deleteById(@Param("id") UUID id);
    
    int deleteByExpenseId(@Param("expenseId") UUID expenseId);
}

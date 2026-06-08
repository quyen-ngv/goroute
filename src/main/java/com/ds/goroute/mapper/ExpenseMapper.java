package com.ds.goroute.mapper;

import com.ds.goroute.entity.Expense;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ExpenseMapper {
    int insert(Expense expense);
    
    Expense selectById(@Param("id") UUID id);
    
    List<Expense> selectAll();
    
    List<Expense> selectByTripId(@Param("tripId") UUID tripId);
    
    List<Expense> selectByActivityId(@Param("activityId") UUID activityId);
    
    List<Expense> selectByPaidBy(@Param("paidBy") UUID paidBy);
    
    int updateById(Expense expense);
    
    int deleteById(@Param("id") UUID id);
    
    int deleteByTripId(@Param("tripId") UUID tripId);
}

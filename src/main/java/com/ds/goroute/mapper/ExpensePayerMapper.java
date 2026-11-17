package com.ds.goroute.mapper;

import com.ds.goroute.entity.ExpensePayer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ExpensePayerMapper {
    int insertBatch(@Param("payers") List<ExpensePayer> payers);

    List<ExpensePayer> selectByExpenseIds(@Param("expenseIds") Collection<UUID> expenseIds);

    int deleteByExpenseId(@Param("expenseId") UUID expenseId);

    int countExpensesPaidByGuest(@Param("guestMemberId") UUID guestMemberId);

    int addGuestAmountsToUser(@Param("guestMemberId") UUID guestMemberId, @Param("userId") UUID userId);

    int deleteGuestRowsMergedIntoUser(@Param("guestMemberId") UUID guestMemberId, @Param("userId") UUID userId);

    int moveGuestRowsToUser(@Param("guestMemberId") UUID guestMemberId, @Param("userId") UUID userId);
}

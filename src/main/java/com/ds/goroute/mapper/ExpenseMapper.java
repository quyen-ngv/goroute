package com.ds.goroute.mapper;

import com.ds.goroute.dto.ExpenseSplitCount;
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

    /** How many expenses still name this guest trip member as the payer. */
    int countByPaidByGuestMemberId(@Param("guestMemberId") UUID guestMemberId);

    /**
     * Moves the payer of every expense this guest paid onto the real user the guest
     * member has just become. For a guest payer both paid_by and
     * paid_by_guest_member_id hold the trip_members id, and the wallet only counts an
     * expense as paid by a user when paid_by_guest_member_id IS NULL.
     */
    int reassignGuestPayerToUser(@Param("guestMemberId") UUID guestMemberId,
                                 @Param("userId") UUID userId);

    /** Split counts for every expense of a trip, so a list does not count them one by one. */
    List<ExpenseSplitCount> selectSplitCountsByTripId(@Param("tripId") UUID tripId);

    int updateById(Expense expense);
    
    int deleteById(@Param("id") UUID id);
    
    int deleteByTripId(@Param("tripId") UUID tripId);
}

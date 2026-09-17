package com.ds.goroute.repository;

import com.ds.goroute.dto.ExpenseSplitCount;
import com.ds.goroute.entity.Expense;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository {
    void insert(Expense expense);
    
    Optional<Expense> findById(UUID id);
    
    List<Expense> findAll();
    
    List<Expense> findByTripId(UUID tripId);
    
    List<Expense> findByActivityId(UUID activityId);
    
    List<Expense> findByPaidBy(UUID userId);

    /** How many expenses still name this guest trip member as the payer. */
    int countByPaidByGuestMemberId(UUID guestMemberId);

    /** Moves the payer of that guest's expenses onto the user the guest became. */
    int reassignGuestPayerToUser(UUID guestMemberId, UUID userId);

    /** Split counts for every expense of a trip, keyed by expense id. */
    List<ExpenseSplitCount> findSplitCountsByTripId(UUID tripId);

    void updateById(Expense expense);
    
    void update(Expense expense); // Alias for updateById
    
    void deleteById(UUID id);
    
    void deleteByTripId(UUID tripId);
}

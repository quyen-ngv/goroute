package com.ds.goroute.repository;

import com.ds.goroute.entity.ExpensePayer;
import com.ds.goroute.mapper.ExpensePayerMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** {@code expense_payers} (V268): who paid towards each expense. */
@Repository
@RequiredArgsConstructor
public class ExpensePayerRepository {

    private final ExpensePayerMapper mapper;

    public void saveAll(List<ExpensePayer> payers) {
        if (payers == null || payers.isEmpty()) return;
        mapper.insertBatch(payers);
    }

    /** Ordered by expense, then position. Empty ids yield an empty list. */
    public List<ExpensePayer> findByExpenseIds(Collection<UUID> expenseIds) {
        if (expenseIds == null || expenseIds.isEmpty()) return List.of();
        List<ExpensePayer> payers = new ArrayList<>();
        for (List<UUID> chunk : Lists.partition(expenseIds.stream().filter(Objects::nonNull).distinct().toList(), 500)) {
            payers.addAll(mapper.selectByExpenseIds(chunk));
        }
        return payers;
    }

    public void deleteByExpenseId(UUID expenseId) {
        mapper.deleteByExpenseId(expenseId);
    }

    /** A guest became this user: their payments become the user's, merged per expense. */
    public void moveGuestToUser(UUID guestMemberId, UUID userId) {
        mapper.addGuestAmountsToUser(guestMemberId, userId);
        mapper.deleteGuestRowsMergedIntoUser(guestMemberId, userId);
        mapper.moveGuestRowsToUser(guestMemberId, userId);
    }
}

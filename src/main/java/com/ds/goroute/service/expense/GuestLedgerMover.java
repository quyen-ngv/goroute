package com.ds.goroute.service.expense;

import com.ds.goroute.repository.ExpensePayerRepository;
import com.ds.goroute.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hands a guest's place in the trip ledger to the account the guest became (epic 05 §2.2).
 *
 * <p>The ledger reads {@code expense_payers} and {@code settlements}; the guest link flows
 * already move {@code expense_splits} and {@code expenses.paid_by*}. Without this the guest's
 * payments and settlements stayed on the guest id, so the linked user showed up twice in the
 * balances (once owing their shares, once owed what they paid) and the suggested transfers
 * asked people to pay a "Guest" nobody could receive for.
 */
@Component
@RequiredArgsConstructor
public class GuestLedgerMover {

    private final ExpensePayerRepository payerRepository;
    private final SettlementRepository settlementRepository;

    /** Expenses this guest paid towards: alone, as one of several payers, or on a pre-V268 row. */
    public int countExpensesPaidBy(UUID guestMemberId) {
        return payerRepository.countExpensesPaidByGuest(guestMemberId);
    }

    /**
     * The guest's payments become the user's (merged into the user's own payment on an expense
     * both paid towards), and so do the settlements the guest gave or got; a settlement between
     * the guest and that same user becomes a payment to oneself and is soft-deleted.
     */
    public void moveToUser(UUID guestMemberId, UUID userId) {
        payerRepository.moveGuestToUser(guestMemberId, userId);
        settlementRepository.moveGuestToUser(guestMemberId, userId);
    }
}

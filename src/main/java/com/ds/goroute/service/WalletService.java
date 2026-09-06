package com.ds.goroute.service;

import com.ds.goroute.dto.response.WalletReminderResponse;
import com.ds.goroute.dto.response.WalletResponse;

import java.util.UUID;

public interface WalletService {
    WalletResponse getWallet(UUID userId, int page, int pageSize, int debtLimit);
    WalletReminderResponse sendReminder(UUID expenseId, UUID splitId, String idempotencyKey, UUID userId);
}

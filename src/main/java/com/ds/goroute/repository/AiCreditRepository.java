package com.ds.goroute.repository;

import com.ds.goroute.entity.AiCreditPlan;
import com.ds.goroute.entity.AiCreditTransaction;
import com.ds.goroute.entity.AiCreditWallet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiCreditRepository {
    /** The wallet row, locked for this transaction; created empty first if the user has none. */
    AiCreditWallet lockWallet(UUID userId);

    void updateWallet(AiCreditWallet wallet);

    boolean hasReference(String referenceKey);

    void insertTransaction(AiCreditTransaction transaction);

    List<AiCreditTransaction> findOpenCharges(UUID userId, String traceId);

    List<AiCreditTransaction> findTransactions(UUID userId, int limit, int offset);

    long countTransactions(UUID userId);

    Optional<AiCreditPlan> findPlan(UUID userId);

    Optional<AiCreditPlan> findPlanByCode(String planCode);
}

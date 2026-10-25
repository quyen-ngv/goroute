package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.AiCreditPlan;
import com.ds.goroute.entity.AiCreditTransaction;
import com.ds.goroute.entity.AiCreditWallet;
import com.ds.goroute.mapper.AiCreditMapper;
import com.ds.goroute.repository.AiCreditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AiCreditRepositoryImpl implements AiCreditRepository {
    private final AiCreditMapper mapper;

    /**
     * Inserts in the caller's own transaction, never REQUIRES_NEW: a nested insert on a second
     * connection would wait on the row lock the suspended caller may already hold.
     */
    @Override
    public AiCreditWallet lockWallet(UUID userId) {
        AiCreditWallet wallet = mapper.findWalletForUpdate(userId);
        if (wallet != null) return wallet;
        mapper.insertWalletIfAbsent(userId);
        return mapper.findWalletForUpdate(userId);
    }

    @Override
    public void updateWallet(AiCreditWallet wallet) {
        mapper.updateWallet(wallet);
    }

    @Override
    public boolean hasReference(String referenceKey) {
        return mapper.countReference(referenceKey) > 0;
    }

    @Override
    public void insertTransaction(AiCreditTransaction transaction) {
        mapper.insertTransaction(transaction);
    }

    @Override
    public List<AiCreditTransaction> findOpenCharges(UUID userId, String traceId) {
        return mapper.findOpenCharges(userId, traceId);
    }

    @Override
    public List<AiCreditTransaction> findTransactions(UUID userId, int limit, int offset) {
        return mapper.findTransactions(userId, limit, offset);
    }

    @Override
    public long countTransactions(UUID userId) {
        return mapper.countTransactions(userId);
    }

    @Override
    public Optional<AiCreditPlan> findPlan(UUID userId) {
        return Optional.ofNullable(mapper.findPlan(userId));
    }

    @Override
    public Optional<AiCreditPlan> findPlanByCode(String planCode) {
        return Optional.ofNullable(mapper.findPlanByCode(planCode));
    }
}

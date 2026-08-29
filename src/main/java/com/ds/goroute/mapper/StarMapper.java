package com.ds.goroute.mapper;

import com.ds.goroute.entity.StarTransaction;
import com.ds.goroute.entity.TripCreationEntitlement;
import com.ds.goroute.entity.UserStarWallet;
import com.ds.goroute.entity.TripCompletionCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface StarMapper {

    UserStarWallet findWallet(@Param("userId") UUID userId);

    /**
     * Reads the wallet with the row locked.
     *
     * <p>Spending has to go through this. Without the lock two simultaneous redemptions
     * both read the same balance, both decide it is sufficient, and both spend it -- the
     * classic way a wallet ends up paying out twice.
     */
    UserStarWallet findWalletForUpdate(@Param("userId") UUID userId);

    void createWallet(@Param("userId") UUID userId);

    int incrementBalance(@Param("userId") UUID userId, @Param("amount") int amount);

    int incrementFreeQuota(@Param("userId") UUID userId);

    int insertTransaction(StarTransaction transaction);

    List<StarTransaction> findTransactions(@Param("userId") UUID userId,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    long countTransactions(@Param("userId") UUID userId);

    StarTransaction findTransactionById(@Param("id") UUID id);

    StarTransaction findTransactionByReference(@Param("referenceKey") String referenceKey);

    int countReference(@Param("referenceKey") String referenceKey);

    /** Sum of every entry, for reconciling the ledger against the stored balance. */
    Integer sumTransactionAmounts(@Param("userId") UUID userId);

    /** Wallets whose balance disagrees with the sum of their entries. */
    List<UUID> findWalletsOutOfBalance(@Param("limit") int limit);

    int insertEntitlement(TripCreationEntitlement entitlement);

    TripCreationEntitlement findActiveEntitlement(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    int consumeEntitlement(@Param("id") UUID id, @Param("now") LocalDateTime now);

    List<TripCompletionCandidate> findEligibleCompletionCandidates();
}

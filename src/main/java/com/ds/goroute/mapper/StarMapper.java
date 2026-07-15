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
    void createWallet(@Param("userId") UUID userId);
    int incrementBalance(@Param("userId") UUID userId, @Param("amount") int amount);
    int incrementFreeQuota(@Param("userId") UUID userId);
    int insertTransaction(StarTransaction transaction);
    List<StarTransaction> findTransactions(@Param("userId") UUID userId, @Param("limit") int limit);
    int countReference(@Param("referenceKey") String referenceKey);
    int insertEntitlement(TripCreationEntitlement entitlement);
    TripCreationEntitlement findActiveEntitlement(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
    int consumeEntitlement(@Param("id") UUID id, @Param("now") LocalDateTime now);
    List<TripCompletionCandidate> findEligibleCompletionCandidates();
}

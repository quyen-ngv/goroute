package com.ds.goroute.mapper;

import com.ds.goroute.entity.AiCreditPlan;
import com.ds.goroute.entity.AiCreditTransaction;
import com.ds.goroute.entity.AiCreditWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AiCreditMapper {
    int insertWalletIfAbsent(@Param("userId") UUID userId);

    AiCreditWallet findWalletForUpdate(@Param("userId") UUID userId);

    int updateWallet(@Param("wallet") AiCreditWallet wallet);

    long countReference(@Param("referenceKey") String referenceKey);

    int insertTransaction(@Param("tx") AiCreditTransaction transaction);

    /** Charges of one task that no refund has reversed yet. */
    List<AiCreditTransaction> findOpenCharges(@Param("userId") UUID userId, @Param("traceId") String traceId);

    List<AiCreditTransaction> findTransactions(@Param("userId") UUID userId,
                                               @Param("limit") int limit, @Param("offset") int offset);

    long countTransactions(@Param("userId") UUID userId);

    BigDecimal sumAmountSince(@Param("userId") UUID userId, @Param("transactionType") String transactionType,
                              @Param("since") LocalDateTime since);

    AiCreditPlan findPlan(@Param("userId") UUID userId);

    /** Allowance of one plan code, for a PRO row that names no plan. */
    AiCreditPlan findPlanByCode(@Param("planCode") String planCode);
}

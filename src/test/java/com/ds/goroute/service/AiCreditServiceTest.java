package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.AiCreditStatus;
import com.ds.goroute.entity.AiApiCall;
import com.ds.goroute.entity.AiCreditPlan;
import com.ds.goroute.entity.AiCreditTransaction;
import com.ds.goroute.entity.AiCreditWallet;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AiCreditRepository;
import com.ds.goroute.type.AiCreditFeature;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.TripCreationCharge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCreditServiceTest {
    private static final UUID USER = UUID.randomUUID();
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final FakeRepository repository = new FakeRepository();
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final BetaAccessService beta = mock(BetaAccessService.class);
    private final StarService stars = mock(StarService.class);
    private AiCreditService service;

    @BeforeEach
    void setUp() {
        service = new AiCreditService(repository, config, beta, mock(UserRealtimePublisher.class), stars);
        service.clock = Clock.fixed(Instant.parse("2026-10-25T03:00:00Z"), ZONE);
        when(config.getDecimal(BusinessConfigKey.AI_CREDIT_MARKUP)).thenReturn(1.2d);
        when(config.getDecimal(BusinessConfigKey.AI_CREDIT_USD_PER_CREDIT)).thenReturn(0.001d);
        when(config.getInt(BusinessConfigKey.AI_CREDIT_FREE_MONTHLY_CREDITS)).thenReturn(1000);
        when(config.getInt(BusinessConfigKey.AI_CREDIT_NEGATIVE_FLOOR)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.AI_CREDIT_MIN_BALANCE_ASSISTANT)).thenReturn(20);
        when(config.getInt(BusinessConfigKey.AI_CREDIT_MIN_BALANCE_AI_TRIP)).thenReturn(100);
        when(config.getInt(BusinessConfigKey.AI_CREDIT_MIN_BALANCE_SOCIAL_LOCATION)).thenReturn(10);
        when(config.getInt(BusinessConfigKey.AI_CREDIT_FALLBACK_CREDITS_PER_CALL)).thenReturn(20);
        when(config.getInt(BusinessConfigKey.AI_CREDIT_TRIP_CREATION_COST)).thenReturn(50);
        when(config.getBoolean(BusinessConfigKey.AI_CREDIT_STAR_EXCHANGE_ENABLED)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.AI_CREDIT_STAR_EXCHANGE_RATE)).thenReturn(10);
        when(config.getInt(BusinessConfigKey.AI_CREDIT_STAR_EXCHANGE_MONTHLY_CAP)).thenReturn(500);
        repository.wallet.setCreatedAt(LocalDateTime.of(2026, 9, 3, 8, 0));
    }

    @Test
    void aNewFreeWalletIsFilledForTheMonthItIsIn() {
        var wallet = service.wallet(USER);

        assertThat(wallet.getBalance()).isEqualByComparingTo("1000");
        assertThat(wallet.getPlanCode()).isEqualTo("FREE");
        assertThat(wallet.getNextRefillAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 8, 0));
        assertThat(repository.transactions).singleElement()
                .satisfies(tx -> assertThat(tx.getTransactionType()).isEqualTo("PLAN_REFILL"));
    }

    @Test
    void aChargeIsTheCostTimesTheMarkupAndIsNeverTakenTwice() {
        AiApiCall call = call(new BigDecimal("0.05"));

        AiCreditStatus status = service.charge(call);
        service.charge(call);

        // 0.05 USD x 1.2 / 0.001 = 60 credits, once.
        assertThat(status.creditBalance()).isEqualByComparingTo("940");
        assertThat(status.creditExhausted()).isFalse();
        assertThat(repository.transactions).filteredOn(tx -> "CHARGE".equals(tx.getTransactionType())).hasSize(1);
    }

    @Test
    void planCreditsGoFirstThenBoughtOnesThenDebt() {
        service.wallet(USER);
        repository.wallet.setPlanBalance(new BigDecimal("30"));
        repository.wallet.setPurchasedBalance(new BigDecimal("20"));

        AiCreditStatus status = service.charge(call(new BigDecimal("0.06")));   // 72 credits

        assertThat(repository.wallet.getPlanBalance()).isEqualByComparingTo("-22");
        assertThat(repository.wallet.getPurchasedBalance()).isEqualByComparingTo("0");
        assertThat(status.creditExhausted()).isTrue();
    }

    @Test
    void earnedCreditsAreSpentAfterThePlanAndBeforeBoughtOnes() {
        service.wallet(USER);
        repository.wallet.setPlanBalance(new BigDecimal("30"));
        repository.wallet.setEarnedBalance(new BigDecimal("20"));
        repository.wallet.setPurchasedBalance(new BigDecimal("100"));

        service.charge(call(new BigDecimal("0.06")));   // 72 credits

        assertThat(repository.wallet.getPlanBalance()).isEqualByComparingTo("0");
        assertThat(repository.wallet.getEarnedBalance()).isEqualByComparingTo("0");
        assertThat(repository.wallet.getPurchasedBalance()).isEqualByComparingTo("78");
    }

    @Test
    void aTripCreationCostsTheConfiguredCreditsOnce() {
        UUID tripId = UUID.randomUUID();

        service.chargeTripCreation(USER, tripId, TripCreationCharge.REQUIRE_BALANCE);
        service.chargeTripCreation(USER, tripId, TripCreationCharge.REQUIRE_BALANCE);

        assertThat(repository.wallet.balance()).isEqualByComparingTo("950");
        assertThat(repository.transactions).filteredOn(tx -> "TRIP_CREATE".equals(tx.getTransactionType()))
                .singleElement()
                .satisfies(tx -> assertThat(tx.getFeature()).isEqualTo("TRIP"));
    }

    /** Builds up to 21 only know 4000101, so a short balance keeps that code with reason CREDIT. */
    @Test
    void aShortBalanceRefusesAManualTripButAnAiDraftMayOverdraw() {
        service.wallet(USER);
        repository.wallet.setPlanBalance(new BigDecimal("40"));

        assertThatThrownBy(() -> service.chargeTripCreation(USER, UUID.randomUUID(), TripCreationCharge.REQUIRE_BALANCE))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code")
                .isEqualTo(ErrorConstant.TRIP_CREATION_QUOTA_EXHAUSTED);

        service.chargeTripCreation(USER, UUID.randomUUID(), TripCreationCharge.ALLOW_OVERDRAFT);
        assertThat(repository.wallet.balance()).isEqualByComparingTo("-10");
    }

    @Test
    void anAiTripNeedsItsMinimumPlusTheTripCost() {
        service.wallet(USER);
        repository.wallet.setPlanBalance(new BigDecimal("120"));

        assertThat(service.canStart(USER, AiCreditFeature.AI_TRIP)).isTrue();
        assertThat(service.canStartTrip(USER, AiCreditFeature.AI_TRIP)).isFalse();
    }

    @Test
    void starsAreExchangedIntoTheEarnedBucketUnderTheMonthlyCap() {
        service.wallet(USER);

        service.exchangeStars(USER, 20);

        verify(stars).lockPoints(USER);
        verify(stars).spend(eq(USER), eq(20), eq("STAR_EXCHANGE"), anyString(), anyString());
        assertThat(repository.wallet.getEarnedBalance()).isEqualByComparingTo("200");
        assertThat(service.wallet(USER).getStarExchange().getRemainingThisPeriod()).isEqualByComparingTo("300");

        assertThatThrownBy(() -> service.exchangeStars(USER, 31))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code")
                .isEqualTo(ErrorConstant.STAR_EXCHANGE_CAP_REACHED);
    }

    @Test
    void aSmallOverdraftLetsTheTaskFinishButBetaIsNeverStopped() {
        service.wallet(USER);
        repository.wallet.setPlanBalance(new BigDecimal("2"));

        assertThat(service.charge(call(new BigDecimal("0.005"))).creditExhausted()).isFalse();   // -4

        when(beta.isBetaUser(USER)).thenReturn(true);
        assertThat(service.charge(call(new BigDecimal("0.5"))).creditExhausted()).isFalse();     // -604
    }

    @Test
    void startingNeedsTheFeatureMinimumAndItineraryFromVideoNeedsBoth() {
        service.wallet(USER);
        repository.wallet.setPlanBalance(new BigDecimal("105"));

        assertThat(service.canStart(USER, AiCreditFeature.AI_TRIP)).isTrue();
        assertThatThrownBy(() -> service.ensureCanStart(USER, AiCreditFeature.AI_TRIP, AiCreditFeature.SOCIAL_LOCATION))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code")
                .isEqualTo(ErrorConstant.AI_CREDIT_INSUFFICIENT);
    }

    @Test
    void aFailedTaskGetsEveryChargeBackOnce() {
        service.charge(call(new BigDecimal("0.05")));
        service.charge(call(new BigDecimal("0.05")));

        service.refundTask(USER, "turn-1", "failed");
        service.refundTask(USER, "turn-1", "failed");

        assertThat(repository.wallet.balance()).isEqualByComparingTo("1000");
        assertThat(repository.transactions).filteredOn(tx -> "REFUND".equals(tx.getTransactionType())).hasSize(2);
    }

    @Test
    void aCallWithoutTokensOrPriceIsFreeButAPricelessCallWithTokensPaysTheFallback() {
        AiApiCall free = call(null);
        free.setInputTokens(0);
        free.setOutputTokens(0);
        AiApiCall unpriced = call(null);

        service.charge(free);
        service.charge(unpriced);

        assertThat(repository.wallet.balance()).isEqualByComparingTo("980");
    }

    @Test
    void theNextPeriodResetsThePlanBucketAndRecoversDebt() {
        service.wallet(USER);
        repository.wallet.setPlanBalance(new BigDecimal("-30"));
        service.clock = Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), ZONE);

        assertThat(service.wallet(USER).getPlanBalance()).isEqualByComparingTo("970");
    }

    @Test
    void aProPlanRefillsOnItsOwnCycleFromTheDayItStarted() {
        repository.plan = AiCreditPlan.builder().tier("PRO").planCode("PRO_YEARLY")
                .startedAt(LocalDateTime.of(2026, 1, 10, 0, 0)).expiresAt(LocalDateTime.of(2027, 1, 10, 0, 0))
                .creditsPerRefill(new BigDecimal("5000")).refillMonths(3).build();

        var wallet = service.wallet(USER);

        assertThat(wallet.getPlanCode()).isEqualTo("PRO_YEARLY");
        assertThat(wallet.getBalance()).isEqualByComparingTo("5000");
        assertThat(wallet.getNextRefillAt()).isEqualTo(LocalDateTime.of(2027, 1, 10, 0, 0));
    }

    private static AiApiCall call(BigDecimal costUsd) {
        return AiApiCall.builder().id(UUID.randomUUID()).userId(USER).feature("ASSISTANT").operation("ASSISTANT_TURN")
                .correlationId(UUID.randomUUID().toString()).traceId("turn-1").model("m")
                .inputTokens(1000).outputTokens(100).costUsd(costUsd).build();
    }

    /** One wallet, an append-only list, and the reference check the real table enforces. */
    private static final class FakeRepository implements AiCreditRepository {
        final AiCreditWallet wallet = AiCreditWallet.builder().userId(USER).planBalance(BigDecimal.ZERO)
                .earnedBalance(BigDecimal.ZERO).purchasedBalance(BigDecimal.ZERO).lifetimeSpent(BigDecimal.ZERO).build();
        final List<AiCreditTransaction> transactions = new ArrayList<>();
        AiCreditPlan plan;

        @Override
        public AiCreditWallet lockWallet(UUID userId) {
            return wallet;
        }

        @Override
        public void updateWallet(AiCreditWallet updated) {
        }

        @Override
        public boolean hasReference(String referenceKey) {
            return transactions.stream().anyMatch(tx -> tx.getReferenceKey().equals(referenceKey));
        }

        @Override
        public void insertTransaction(AiCreditTransaction transaction) {
            transactions.add(transaction);
        }

        @Override
        public List<AiCreditTransaction> findOpenCharges(UUID userId, String traceId) {
            return transactions.stream()
                    .filter(tx -> "CHARGE".equals(tx.getTransactionType()) && traceId.equals(tx.getTraceId()))
                    .filter(tx -> transactions.stream().noneMatch(r -> tx.getId().equals(r.getReversesTransactionId())))
                    .toList();
        }

        @Override
        public List<AiCreditTransaction> findTransactions(UUID userId, int limit, int offset) {
            return transactions;
        }

        @Override
        public long countTransactions(UUID userId) {
            return transactions.size();
        }

        @Override
        public BigDecimal sumAmountSince(UUID userId, String transactionType, LocalDateTime since) {
            return transactions.stream()
                    .filter(tx -> transactionType.equals(tx.getTransactionType()))
                    .map(AiCreditTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public Optional<AiCreditPlan> findPlan(UUID userId) {
            return Optional.ofNullable(plan);
        }

        @Override
        public Optional<AiCreditPlan> findPlanByCode(String planCode) {
            return Optional.empty();
        }
    }
}

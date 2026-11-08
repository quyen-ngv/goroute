package com.ds.goroute.service.expense;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.expense.SplitCalculator.ShareInput;
import com.ds.goroute.type.SplitMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitCalculatorTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static ShareInput input(String shareInput) {
        return new ShareInput(null, shareInput == null ? null : money(shareInput));
    }

    private static ShareInput exact(String amount) {
        return new ShareInput(money(amount), null);
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("an even VND split puts the leftover dong on the first people, in whole dong")
    void equalVndRemainderGoesToTheFirstPeople() {
        List<BigDecimal> amounts = SplitCalculator.split(SplitMethod.EQUAL, money("100000"), "VND",
                List.of(input(null), input(null), input(null)));

        assertThat(amounts).extracting(BigDecimal::toPlainString).containsExactly("33334", "33333", "33333");
        assertThat(sum(amounts)).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("an even USD split works in cents")
    void equalUsdUsesCents() {
        List<BigDecimal> amounts = SplitCalculator.split(SplitMethod.EQUAL, money("10.00"), "USD",
                List.of(input(null), input(null), input(null)));

        assertThat(amounts).extracting(BigDecimal::toPlainString).containsExactly("3.34", "3.33", "3.33");
    }

    @Test
    @DisplayName("exact amounts are kept when they add up")
    void exactKeepsAmountsThatAddUp() {
        List<BigDecimal> amounts = SplitCalculator.split(SplitMethod.EXACT, money("300000"), "VND",
                List.of(exact("100000"), exact("200000")));

        assertThat(amounts).containsExactly(money("100000"), money("200000"));
    }

    @Test
    @DisplayName("an old build's unrounded amount / n is re-spread to the exact total")
    void exactRepairsUnroundedLegacyShares() {
        List<BigDecimal> amounts = SplitCalculator.split(SplitMethod.EXACT, money("100000"), "VND",
                List.of(exact("33333.333333"), exact("33333.333333"), exact("33333.333333")));

        assertThat(sum(amounts)).isEqualByComparingTo("100000");
        assertThat(amounts).allSatisfy(amount -> assertThat(amount.stripTrailingZeros().scale()).isLessThanOrEqualTo(0));
    }

    @Test
    @DisplayName("exact amounts that do not add up are refused")
    void exactRefusesAWrongSum() {
        assertThatThrownBy(() -> SplitCalculator.split(SplitMethod.EXACT, money("300000"), "VND",
                List.of(exact("100000"), exact("100000"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("add up");
    }

    @Test
    @DisplayName("percentages split pro rata and must make 100")
    void percentages() {
        List<BigDecimal> amounts = SplitCalculator.split(SplitMethod.PERCENT, money("200000"), "VND",
                List.of(input("50"), input("30"), input("20")));

        assertThat(amounts).containsExactly(money("100000"), money("60000"), money("40000"));
        assertThatThrownBy(() -> SplitCalculator.split(SplitMethod.PERCENT, money("200000"), "VND",
                List.of(input("50"), input("30"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("three times 33.33% is still the whole amount")
    void percentagesTolerateTypedThirds() {
        List<BigDecimal> amounts = SplitCalculator.split(SplitMethod.PERCENT, money("90000"), "VND",
                List.of(input("33.33"), input("33.33"), input("33.33")));

        assertThat(sum(amounts)).isEqualByComparingTo("90000");
    }

    @Test
    @DisplayName("shares split by weight and must all be above zero")
    void shares() {
        List<BigDecimal> amounts = SplitCalculator.split(SplitMethod.SHARES, money("300000"), "VND",
                List.of(input("2"), input("1")));

        assertThat(amounts).containsExactly(money("200000"), money("100000"));
        assertThatThrownBy(() -> SplitCalculator.split(SplitMethod.SHARES, money("300000"), "VND",
                List.of(input("2"), input("0"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("an adjustment is added to one person and the rest split evenly")
    void adjustment() {
        List<BigDecimal> amounts = SplitCalculator.split(SplitMethod.ADJUSTMENT, money("300000"), "VND",
                List.of(input("60000"), input(null), input(null)));

        assertThat(amounts).containsExactly(money("140000"), money("80000"), money("80000"));
        assertThat(sum(amounts)).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("adjustments larger than the amount are refused")
    void adjustmentAboveTheAmount() {
        assertThatThrownBy(() -> SplitCalculator.split(SplitMethod.ADJUSTMENT, money("100000"), "VND",
                List.of(input("150000"), input(null))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("no people or a non-positive amount is refused")
    void refusesEmptyInput() {
        assertThatThrownBy(() -> SplitCalculator.split(SplitMethod.EQUAL, money("100"), "VND", List.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> SplitCalculator.split(SplitMethod.EQUAL, BigDecimal.ZERO, "VND", List.of(input(null))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("an even split is recognised in both the new and the pre-V268 rounding")
    void recognisesEvenSplits() {
        assertThat(SplitCalculator.isEvenSplit(money("100000"), "VND",
                List.of(money("33334"), money("33333"), money("33333")))).isTrue();
        assertThat(SplitCalculator.isEvenSplit(money("100000"), "VND",
                List.of(money("33333"), money("33333"), money("33334")))).isTrue();
        assertThat(SplitCalculator.isEvenSplit(money("100000"), "VND",
                List.of(money("50000"), money("30000"), money("20000")))).isFalse();
    }

    @Test
    @DisplayName("an unknown currency code falls back to two decimals")
    void unknownCurrencyUsesTwoDecimals() {
        assertThat(SplitCalculator.fractionDigits("XXZ", money("10"))).isEqualTo(2);
        assertThat(SplitCalculator.fractionDigits("VND", money("10"))).isZero();
        assertThat(SplitCalculator.fractionDigits("JPY", money("10"))).isZero();
    }
}

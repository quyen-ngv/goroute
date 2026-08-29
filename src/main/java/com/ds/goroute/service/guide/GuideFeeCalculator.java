package com.ds.goroute.service.guide;

import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.GuidePricingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Works out what a booking costs, what the platform keeps and what the guide receives
 * (GUIDE-05).
 *
 * <p>The percentage is configuration with a version attached, not a constant. Both the
 * rate and the version are copied onto the booking when it is made, and nothing reads them
 * again afterwards: changing the commission has to apply to new bookings only, or the
 * platform is quietly rewriting deals people already agreed to.
 */
@Component
@RequiredArgsConstructor
public class GuideFeeCalculator {

    /** VND has no minor unit, so money is rounded to whole currency units. */
    private static final int MONEY_SCALE = 0;

    /** The numbers written onto a booking, all of them derived once and then frozen. */
    public record Breakdown(BigDecimal serviceAmount,
                            BigDecimal platformFeePercent,
                            BigDecimal platformFeeAmount,
                            BigDecimal guidePayoutAmount,
                            String feeRuleVersion) {
    }

    private final BusinessConfigService config;

    public Breakdown calculate(GuidePricingMode pricingMode, BigDecimal unitPrice, int guestCount) {
        BigDecimal serviceAmount = pricingMode.totalFor(unitPrice, guestCount)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal feePercent = BigDecimal.valueOf(
                config.getDecimal(BusinessConfigKey.GUIDE_PLATFORM_FEE_PERCENT));

        BigDecimal feeAmount = serviceAmount
                .multiply(feePercent)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
        // Subtraction rather than a second percentage, so the two halves always add back
        // up to what the traveller paid and no rounding remainder goes missing.
        BigDecimal payout = serviceAmount.subtract(feeAmount);

        return new Breakdown(serviceAmount, feePercent, feeAmount, payout,
                config.getText(BusinessConfigKey.GUIDE_FEE_RULE_VERSION));
    }
}

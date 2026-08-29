package com.ds.goroute.type;

/**
 * How a price is read.
 *
 * <p>Stated explicitly because "500,000" without saying per person or per group is the
 * kind of ambiguity that turns into an argument at the meeting point.
 */
public enum GuidePricingMode {
    PER_PERSON,
    PER_GROUP;

    public java.math.BigDecimal totalFor(java.math.BigDecimal unitPrice, int guestCount) {
        return this == PER_PERSON
                ? unitPrice.multiply(java.math.BigDecimal.valueOf(guestCount))
                : unitPrice;
    }
}

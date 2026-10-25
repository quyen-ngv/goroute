package com.ds.goroute.type;

import java.util.Locale;

/**
 * AI features a user triggers and pays for in credits. The name is the {@code ai_api_calls.feature}
 * the feature logs under; moderation and other system calls are not here and are never charged.
 */
public enum AiCreditFeature {
    ASSISTANT(BusinessConfigKey.AI_CREDIT_MIN_BALANCE_ASSISTANT),
    AI_TRIP(BusinessConfigKey.AI_CREDIT_MIN_BALANCE_AI_TRIP),
    RECEIPT_SCAN(BusinessConfigKey.AI_CREDIT_MIN_BALANCE_RECEIPT_SCAN),
    SOCIAL_LOCATION(BusinessConfigKey.AI_CREDIT_MIN_BALANCE_SOCIAL_LOCATION);

    private final BusinessConfigKey minBalanceKey;

    AiCreditFeature(BusinessConfigKey minBalanceKey) {
        this.minBalanceKey = minBalanceKey;
    }

    /** The balance needed to start this feature. */
    public BusinessConfigKey minBalanceKey() {
        return minBalanceKey;
    }

    /** The billed feature an {@code ai_api_calls.feature} value names, or null when it is not billed. */
    public static AiCreditFeature fromLogFeature(String feature) {
        if (feature == null) return null;
        try {
            return valueOf(feature.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notBilled) {
            return null;
        }
    }
}

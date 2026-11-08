package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What exchanging Stars into credits would give right now. Amounts are credits. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StarExchangeTerms {
    /** AI_CREDIT.STAR_EXCHANGE_ENABLED. */
    private boolean enabled;
    /** Credits one Star buys. */
    private int rate;
    /** Credits that may come from Stars in one credit-wallet period. */
    private int monthlyCap;
    private BigDecimal usedThisPeriod;
    private BigDecimal remainingThisPeriod;
    /** Most Stars that may be exchanged now under the cap (the Star balance is not counted). */
    private int maxStarsThisPeriod;
    /** When the period, and with it the cap, starts over. */
    private LocalDateTime periodEndsAt;
}

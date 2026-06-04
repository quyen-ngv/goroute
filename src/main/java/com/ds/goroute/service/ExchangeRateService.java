package com.ds.goroute.service;

import java.math.BigDecimal;

public interface ExchangeRateService {
    /**
     * Convert amount from one currency to another using cached daily rates.
     * Returns the original amount when from == to or rate cannot be fetched.
     */
    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency);

    /**
     * Get the exchange rate from -> to (1 unit of fromCurrency = ? toCurrency).
     * Returns BigDecimal.ONE when rate is unavailable or currencies are equal.
     */
    BigDecimal getRate(String fromCurrency, String toCurrency);
}

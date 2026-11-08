package com.ds.goroute.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateService {
    /**
     * Convert amount from one currency to another using cached daily rates.
     * Returns the original amount when from == to or rate cannot be fetched.
     */
    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency);

    /**
     * Get the exchange rate from -> to (1 unit of fromCurrency = ? toCurrency).
     * Returns BigDecimal.ONE when rate is unavailable or currencies are equal.
     * Display paths only: money that is stored must use {@link #findRate} or {@link #findRateOn}.
     */
    BigDecimal getRate(String fromCurrency, String toCurrency);

    /**
     * Today's rate (1 unit of {@code fromCurrency} in {@code toCurrency}); 1 for the same
     * currency, empty when the provider has none or cannot be reached. Never guesses 1:1.
     */
    Optional<BigDecimal> findRate(String fromCurrency, String toCurrency);

    /**
     * The rate published for {@code date} ({@code currency-api@YYYY-MM-DD}), for money spent that
     * day. Falls back to today's rate when the day has no snapshot (today's file may not be out
     * yet); empty when neither exists.
     */
    Optional<BigDecimal> findRateOn(String fromCurrency, String toCurrency, LocalDate date);
}

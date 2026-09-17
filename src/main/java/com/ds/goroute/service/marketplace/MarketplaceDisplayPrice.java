package com.ds.goroute.service.marketplace;

import com.ds.goroute.config.filter.AcceptCurrencyFilter;
import com.ds.goroute.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders marketplace catalogue prices in the currency the guest asked for with
 * the {@code Accept-Currency} header.
 *
 * <p>Browsing only. A partner prices a rate plan or a package in one currency and
 * settles in that currency, so bookings and orders keep the stored amount; this
 * exists so a guest can compare a VND stay with a USD tour without doing the
 * arithmetic. Apply it to public read responses only — partner and admin
 * endpoints must keep returning the authored price, or an edit would save back a
 * converted number.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceDisplayPrice {

    private final ExchangeRateService exchangeRateService;

    /** Currency the current request should be rendered in. */
    public String target() {
        return AcceptCurrencyFilter.current();
    }

    /**
     * Currency to report for an amount stored in {@code storedCurrency} — the
     * stored one whenever {@link #convert} leaves the amount alone, so a number
     * can never be published under a currency it was not converted into.
     */
    public String currencyOf(String storedCurrency) {
        if (storedCurrency == null || storedCurrency.isBlank()) return target();
        return rateFrom(storedCurrency) == null ? storedCurrency.toUpperCase() : target();
    }

    /**
     * Null stays null: an absent price is not a zero price, which is what
     * {@link ExchangeRateService#convert} would make of it.
     */
    public BigDecimal convert(BigDecimal amount, String storedCurrency) {
        if (amount == null) return null;
        BigDecimal rate = rateFrom(storedCurrency);
        return rate == null ? amount : amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Per-guest-type price maps (adult, child, …) carry their package's currency. */
    public Map<String, BigDecimal> convert(Map<String, BigDecimal> prices, String storedCurrency) {
        if (prices == null || prices.isEmpty()) return prices;
        Map<String, BigDecimal> converted = new LinkedHashMap<>();
        prices.forEach((key, value) -> converted.put(key, convert(value, storedCurrency)));
        return converted;
    }

    /**
     * Null when the price has to stay exactly as stored: same currency, or the
     * rate provider is unreachable. {@link ExchangeRateService#getRate} reports
     * that outage as a rate of one, and showing a VND number under a dollar sign
     * is worse than showing it in dong.
     */
    private BigDecimal rateFrom(String storedCurrency) {
        String target = target();
        if (storedCurrency == null || storedCurrency.isBlank()
                || storedCurrency.equalsIgnoreCase(target)) {
            return null;
        }
        BigDecimal rate = exchangeRateService.getRate(storedCurrency, target);
        return (rate == null || rate.compareTo(BigDecimal.ONE) == 0) ? null : rate;
    }
}

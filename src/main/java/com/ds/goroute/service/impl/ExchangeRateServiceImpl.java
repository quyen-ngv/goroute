package com.ds.goroute.service.impl;

import com.ds.goroute.service.ExchangeRateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private static final String PRIMARY_URL =
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/%s.json";
    private static final String FALLBACK_URL =
            "https://latest.currency-api.pages.dev/v1/currencies/%s.json";
    /** The same files, frozen per day: {@code @YYYY-MM-DD} instead of {@code @latest}. */
    private static final String DATED_PRIMARY_URL =
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@%s/v1/currencies/%s.json";
    private static final String DATED_FALLBACK_URL =
            "https://%s.currency-api.pages.dev/v1/currencies/%s.json";

    private final RestTemplate exchangeRestTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) return BigDecimal.ZERO;
        BigDecimal rate = getRate(fromCurrency, toCurrency);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * A failed or missing lookup falls back to 1, so the caller still gets an amount; that
     * fallback must not be cached, or one upstream blip would price everything 1:1 for the
     * cache's six hours. A genuine 1:1 pair is simply fetched again.
     */
    @Override
    @Cacheable(value = "exchangeRates",
               key = "#fromCurrency.toLowerCase() + '_' + #toCurrency.toLowerCase()",
               cacheManager = "exchangeRateCacheManager",
               unless = "#result == null || #result.compareTo(T(java.math.BigDecimal).ONE) == 0")
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null) return BigDecimal.ONE;
        return lookup(fromCurrency, toCurrency, null).orElse(BigDecimal.ONE);
    }

    /** An empty answer is not cached (Spring unwraps the Optional for {@code unless}). */
    @Override
    @Cacheable(value = "exchangeRates",
               key = "'opt_' + #fromCurrency.toLowerCase() + '_' + #toCurrency.toLowerCase()",
               cacheManager = "exchangeRateCacheManager",
               unless = "#result == null")
    public Optional<BigDecimal> findRate(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null) return Optional.empty();
        return lookup(fromCurrency, toCurrency, null);
    }

    @Override
    @Cacheable(value = "exchangeRates",
               key = "'d_' + #date + '_' + #fromCurrency.toLowerCase() + '_' + #toCurrency.toLowerCase()",
               cacheManager = "exchangeRateCacheManager",
               unless = "#result == null")
    public Optional<BigDecimal> findRateOn(String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency == null || toCurrency == null) return Optional.empty();
        if (date != null && date.isBefore(LocalDate.now())) {
            Optional<BigDecimal> dated = lookup(fromCurrency, toCurrency, date);
            if (dated.isPresent()) return dated;
            log.info("No {} snapshot for {}->{}, using the latest rate", date, fromCurrency, toCurrency);
        }
        return lookup(fromCurrency, toCurrency, null);
    }

    /** @param date null for the latest rates */
    private Optional<BigDecimal> lookup(String fromCurrency, String toCurrency, LocalDate date) {
        String from = fromCurrency.toLowerCase(Locale.ROOT);
        String to = toCurrency.toLowerCase(Locale.ROOT);
        if (from.equals(to)) return Optional.of(BigDecimal.ONE);
        try {
            return fetchRate(from, to, date);
        } catch (Exception e) {
            log.warn("Exchange rate fetch failed {}->{} ({}): {}", from, to,
                    date == null ? "latest" : date, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> fetchRate(String from, String to, LocalDate date) {
        JsonNode root = parseJson(fetchJson(primaryUrl(from, date), fallbackUrl(from, date)));
        JsonNode rateNode = root.path(from).path(to);

        if (rateNode.isMissingNode() || rateNode.isNull()) {
            // Try inverse: to -> from, then invert
            JsonNode inverseRoot = parseJson(fetchJson(primaryUrl(to, date), fallbackUrl(to, date)));
            JsonNode inverseRate = inverseRoot.path(to).path(from);
            if (!inverseRate.isMissingNode() && !inverseRate.isNull()) {
                BigDecimal inv = new BigDecimal(inverseRate.asText());
                if (inv.compareTo(BigDecimal.ZERO) > 0) {
                    return Optional.of(BigDecimal.ONE.divide(inv, 10, RoundingMode.HALF_UP));
                }
            }
            log.warn("No rate found for {} -> {}", from, to);
            return Optional.empty();
        }

        BigDecimal rate = new BigDecimal(rateNode.asText()).round(new MathContext(10, RoundingMode.HALF_UP));
        return rate.signum() > 0 ? Optional.of(rate) : Optional.empty();
    }

    private static String primaryUrl(String currency, LocalDate date) {
        return date == null ? String.format(PRIMARY_URL, currency) : String.format(DATED_PRIMARY_URL, date, currency);
    }

    private static String fallbackUrl(String currency, LocalDate date) {
        return date == null ? String.format(FALLBACK_URL, currency) : String.format(DATED_FALLBACK_URL, date, currency);
    }

    private String fetchJson(String primaryUrl, String fallbackUrl) {
        try {
            ResponseEntity<String> response = exchangeRestTemplate.getForEntity(primaryUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Primary exchange URL failed, trying fallback: {}", e.getMessage());
        }
        ResponseEntity<String> fallback = exchangeRestTemplate.getForEntity(fallbackUrl, String.class);
        if (!fallback.getStatusCode().is2xxSuccessful() || fallback.getBody() == null) {
            throw new RuntimeException("Both exchange rate URLs failed");
        }
        return fallback.getBody();
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse exchange rate JSON: " + e.getMessage(), e);
        }
    }
}

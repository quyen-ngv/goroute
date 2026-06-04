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
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private static final String PRIMARY_URL =
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/%s.json";
    private static final String FALLBACK_URL =
            "https://latest.currency-api.pages.dev/v1/currencies/%s.json";

    private final RestTemplate exchangeRestTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) return BigDecimal.ZERO;
        BigDecimal rate = getRate(fromCurrency, toCurrency);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Cacheable(value = "exchangeRates",
               key = "#fromCurrency.toLowerCase() + '_' + #toCurrency.toLowerCase()",
               cacheManager = "exchangeRateCacheManager")
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null) return BigDecimal.ONE;
        String from = fromCurrency.toLowerCase(Locale.ROOT);
        String to = toCurrency.toLowerCase(Locale.ROOT);
        if (from.equals(to)) return BigDecimal.ONE;

        try {
            return fetchRate(from, to);
        } catch (Exception e) {
            log.warn("Exchange rate fetch failed {}->{}: {}", from, to, e.getMessage());
            return BigDecimal.ONE;
        }
    }

    private BigDecimal fetchRate(String from, String to) {
        String json = fetchJson(String.format(PRIMARY_URL, from),
                String.format(FALLBACK_URL, from));

        JsonNode root = parseJson(json);
        JsonNode rateNode = root.path(from).path(to);

        if (rateNode.isMissingNode()) {
            // Try inverse: to -> from, then invert
            String inverseJson = fetchJson(String.format(PRIMARY_URL, to),
                    String.format(FALLBACK_URL, to));
            JsonNode inverseRoot = parseJson(inverseJson);
            JsonNode inverseRate = inverseRoot.path(to).path(from);
            if (!inverseRate.isMissingNode()) {
                BigDecimal inv = new BigDecimal(inverseRate.asText());
                if (inv.compareTo(BigDecimal.ZERO) > 0) {
                    return BigDecimal.ONE.divide(inv, 10, RoundingMode.HALF_UP);
                }
            }
            log.warn("No rate found for {} -> {}", from, to);
            return BigDecimal.ONE;
        }

        return new BigDecimal(rateNode.asText()).round(new MathContext(10, RoundingMode.HALF_UP));
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

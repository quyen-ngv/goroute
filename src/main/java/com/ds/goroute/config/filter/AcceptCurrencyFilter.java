package com.ds.goroute.config.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Reads the Accept-Currency request header and makes it available via a
 * thread-local so any service layer code can access it without needing
 * the HttpServletRequest directly.
 */
@Component
@Order(1)
public class AcceptCurrencyFilter implements Filter {

    public static final String HEADER_NAME = "Accept-Currency";
    public static final String DEFAULT_CURRENCY = "USD";

    private static final Set<String> ALLOWED = Set.of(
        "USD", "VND", "EUR", "GBP", "JPY", "KRW", "SGD", "THB",
        "AUD", "CAD", "CHF", "HKD", "TWD", "MYR", "IDR", "PHP"
    );

    private static final ThreadLocal<String> CURRENCY_HOLDER = new ThreadLocal<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            String raw = null;
            if (request instanceof HttpServletRequest httpReq) {
                raw = httpReq.getHeader(HEADER_NAME);
            }
            String currency = normalize(raw);
            CURRENCY_HOLDER.set(currency);
            chain.doFilter(request, response);
        } finally {
            CURRENCY_HOLDER.remove();
        }
    }

    /** Returns the currency requested by the current request thread. */
    public static String current() {
        String c = CURRENCY_HOLDER.get();
        return (c != null) ? c : DEFAULT_CURRENCY;
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_CURRENCY;
        String upper = raw.trim().toUpperCase();
        return ALLOWED.contains(upper) ? upper : DEFAULT_CURRENCY;
    }
}

package com.ds.goroute.config.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Reads Accept-Language and exposes the normalized app language code
 * for the current request thread (also sets LocaleContextHolder for i18n).
 */
@Component
@Order(2)
public class AcceptLanguageFilter implements Filter {

    public static final String HEADER_NAME = "Accept-Language";
    public static final String DEFAULT_LANGUAGE = "en";

    private static final Set<String> ALLOWED = Set.of(
            "en", "vi", "ko", "th", "ja", "zh-tw", "ru", "hi"
    );

    private static final ThreadLocal<String> LANGUAGE_HOLDER = new ThreadLocal<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            String raw = null;
            if (request instanceof HttpServletRequest httpReq) {
                raw = httpReq.getHeader(HEADER_NAME);
            }
            String language = normalize(raw);
            LANGUAGE_HOLDER.set(language);
            LocaleContextHolder.setLocale(toLocale(language));
            chain.doFilter(request, response);
        } finally {
            LANGUAGE_HOLDER.remove();
            LocaleContextHolder.resetLocaleContext();
        }
    }

    public static String currentCode() {
        String code = LANGUAGE_HOLDER.get();
        return code != null ? code : DEFAULT_LANGUAGE;
    }

    public static Locale currentLocale() {
        return toLocale(currentCode());
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String tag = raw.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if (tag.contains(";")) {
            tag = tag.substring(0, tag.indexOf(';')).trim();
        }
        tag = tag.replace('_', '-');
        if (tag.startsWith("zh")) {
            return "zh-TW";
        }
        if (ALLOWED.contains(tag)) {
            return tag.equals("zh-tw") ? "zh-TW" : tag;
        }
        String primary = tag.contains("-") ? tag.substring(0, tag.indexOf('-')) : tag;
        if ("zh".equals(primary)) {
            return "zh-TW";
        }
        return ALLOWED.contains(primary) ? primary : DEFAULT_LANGUAGE;
    }

    private static Locale toLocale(String code) {
        if ("zh-TW".equalsIgnoreCase(code)) {
            return Locale.forLanguageTag("zh-TW");
        }
        return Locale.forLanguageTag(code);
    }
}

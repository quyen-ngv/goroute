package com.ds.goroute.config.filter;

import com.ds.goroute.type.TranslationLocale;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/**
 * Reads Accept-Language and exposes the normalized app language code
 * for the current request thread (also sets LocaleContextHolder for i18n).
 */
@Component
@Order(2)
public class AcceptLanguageFilter implements Filter {

    public static final String HEADER_NAME = "Accept-Language";
    public static final String DEFAULT_LANGUAGE = "en";

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

    /** Binds {@code code} to this thread for work handed over by {@link RequestContextTaskDecorator}. */
    static void bind(String code) {
        LANGUAGE_HOLDER.set(code);
    }

    static void unbind() {
        LANGUAGE_HOLDER.remove();
    }

    /**
     * The first tag of the header as one of the {@link TranslationLocale} codes (vi, en, ko, th,
     * ja, zh-CN, zh-TW, ru, hi, fr, de, es, it, pt), normalised the same way; {@code en} otherwise.
     */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String tag = raw.split(",")[0].trim();
        if (tag.contains(";")) {
            tag = tag.substring(0, tag.indexOf(';')).trim();
        }
        return TranslationLocale.fromCode(tag).map(TranslationLocale::code).orElse(DEFAULT_LANGUAGE);
    }

    private static Locale toLocale(String code) {
        return Locale.forLanguageTag(code);
    }
}

package com.ds.goroute.config.filter;

import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.TaskDecorator;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

/**
 * Carries the request's language, currency, device time zone, locale and MDC onto a pool
 * thread, so a read split off a request answers exactly as it would on the request thread.
 * Everything is cleared afterwards: pool threads are reused by other requests.
 */
public class RequestContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        String language = AcceptLanguageFilter.currentCode();
        String currency = AcceptCurrencyFilter.current();
        ZoneId zone = ClientTimeZoneFilter.currentZone();
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Thread origin = Thread.currentThread();
        return () -> {
            if (Thread.currentThread() == origin) {
                // A full queue runs the task on the caller (CallerRunsPolicy): its context is
                // already there, and clearing it afterwards would strip the rest of the request.
                task.run();
                return;
            }
            AcceptLanguageFilter.bind(language);
            AcceptCurrencyFilter.bind(currency);
            ClientTimeZoneFilter.bind(zone);
            LocaleContextHolder.setLocale(locale);
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                task.run();
            } finally {
                AcceptLanguageFilter.unbind();
                AcceptCurrencyFilter.unbind();
                ClientTimeZoneFilter.unbind();
                LocaleContextHolder.resetLocaleContext();
                MDC.clear();
            }
        };
    }
}

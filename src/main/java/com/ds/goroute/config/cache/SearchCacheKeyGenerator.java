package com.ds.goroute.config.cache;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import org.springframework.cache.interceptor.KeyGenerator;

import java.lang.reflect.Method;
import java.util.Objects;

/** Generates safe keys for the localized, public catalogue search caches. */
public class SearchCacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder key = new StringBuilder()
                .append(target.getClass().getName())
                .append('.')
                .append(method.getName())
                .append("|language=")
                .append(AcceptLanguageFilter.currentCode());

        for (Object param : params) {
            key.append('|').append(Objects.toString(param, "null"));
        }
        return key.toString();
    }
}

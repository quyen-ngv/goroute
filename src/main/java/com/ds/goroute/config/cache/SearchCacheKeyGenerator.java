package com.ds.goroute.config.cache;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import org.springframework.cache.interceptor.KeyGenerator;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;

/** Generates safe keys for the localized, public catalogue search caches. */
public class SearchCacheKeyGenerator implements KeyGenerator {

    /**
     * 4 decimal places of a degree is about 11 m: callers that close share one entry. Kept
     * this tight because the cached list (radius filter, distance-weighted ranking) was
     * computed from the first caller's point; distances are re-measured per caller
     * ({@code PlaceService.withDistanceFrom}).
     */
    static final int COORDINATE_KEY_SCALE = 4;

    private final Set<Integer> coordinateParamIndexes;

    public SearchCacheKeyGenerator() {
        this(Set.of());
    }

    /**
     * @param coordinateParamIndexes positions of latitude/longitude parameters, rounded to
     *                               {@link #COORDINATE_KEY_SCALE} decimals in the key so a
     *                               map moved a few metres reuses the cached search
     */
    public SearchCacheKeyGenerator(Set<Integer> coordinateParamIndexes) {
        this.coordinateParamIndexes = Set.copyOf(coordinateParamIndexes);
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder key = new StringBuilder()
                .append(target.getClass().getName())
                .append('.')
                .append(method.getName())
                .append("|language=")
                .append(AcceptLanguageFilter.currentCode());

        for (int index = 0; index < params.length; index++) {
            Object param = params[index];
            if (param instanceof BigDecimal coordinate && coordinateParamIndexes.contains(index)) {
                param = coordinate.setScale(COORDINATE_KEY_SCALE, RoundingMode.HALF_UP).toPlainString();
            }
            key.append('|').append(Objects.toString(param, "null"));
        }
        return key.toString();
    }
}

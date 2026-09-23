package com.ds.goroute.service.impl;

import com.ds.goroute.entity.AppConfig;
import com.ds.goroute.repository.AppConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The beta list, parsed once and cached.
 *
 * <p>Its own bean for the same reason as {@link BusinessConfigStore}: caching only works
 * through the Spring proxy. It shares the {@code businessConfig} cache so that editing
 * any config row in the admin console — including this one — drops it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BetaUserStore {

    private static final String LABEL = "USER";
    private static final String KEY = "BETA_USER";

    /** Historic format: usernames separated by commas, semicolons or whitespace. */
    private static final String DELIMITERS = "[,;\\s]+";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final AppConfigRepository repository;
    private final ObjectMapper objectMapper;

    /** Lower-cased, so callers compare without caring how the row was typed. */
    @Cacheable(cacheNames = "businessConfig", key = "'USER:BETA_USER:set'", sync = true)
    public Set<String> usernames() {
        return repository.findActiveByLabelAndKey(LABEL, KEY)
                .map(AppConfig::getValue)
                .map(this::parse)
                .orElseGet(Set::of);
    }

    /**
     * Accepts a JSON array or a delimited string, matching what the app has always
     * parsed. A row saved as {@code ["ann","bob"]} and one saved as {@code ann, bob}
     * mean the same thing, and an operator should not have to know which.
     */
    private Set<String> parse(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return Set.of();
        if (value.startsWith("[")) {
            try {
                return normalize(objectMapper.readValue(value, STRING_LIST));
            } catch (Exception e) {
                // A malformed array is more likely a typo than a new format, and the
                // delimiter parser still finds the names inside it.
                log.warn("Beta user list is not valid JSON; reading it as a delimited list instead");
            }
        }
        return normalize(List.of(value.split(DELIMITERS)));
    }

    /**
     * Strips the punctuation a half-written JSON array leaves behind, so {@code ["ann"}
     * still reads as {@code ann}. A username never legitimately contains any of it.
     */
    private static final String PUNCTUATION = "^[\\[\\]\"']+|[\\[\\]\"']+$";

    private static Set<String> normalize(List<String> names) {
        return names.stream()
                .filter(name -> name != null)
                .map(name -> name.trim().replaceAll(PUNCTUATION, "").trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}

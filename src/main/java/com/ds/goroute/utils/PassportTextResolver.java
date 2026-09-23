package com.ds.goroute.utils;

import com.ds.goroute.config.filter.AcceptLanguageFilter;

/**
 * Picks the Passport catalogue copy for the request language. The catalogue holds a
 * Vietnamese source text and an optional English copy: Vietnamese readers get the source,
 * every other language gets English, and a missing English copy falls back to the source.
 */
public final class PassportTextResolver {

    private PassportTextResolver() {
    }

    public static String resolve(String vietnamese, String english) {
        if ("vi".equalsIgnoreCase(AcceptLanguageFilter.currentCode())
                || english == null || english.isBlank()) {
            return vietnamese;
        }
        return english;
    }
}

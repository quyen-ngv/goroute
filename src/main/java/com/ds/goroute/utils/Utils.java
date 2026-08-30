package com.ds.goroute.utils;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks sensitive values before a request or response body reaches the log.
 *
 * <p>This class used to carry fifteen further helpers -- address building, accent
 * stripping, approximate search, reflection-based property scanning -- none of which had
 * a single caller left. They were removed rather than kept as examples, because the
 * approximate-search pair in particular compared a lower-cased haystack against a
 * non-lower-cased needle and would have matched almost anything if revived.
 */
public class Utils {

    private static final String REGEX_FILTER_KEY =
            "[ : ]+((?=\\[)\\[[^]]*\\]|(?=\\{)\\{[^\\}]*\\}|\\\"[^\"]*\\\"|(\\d+(\\.\\d+)?))";

    private static final String MASK = "\"**********\"";

    private static final List<String> REDACT_KEYS = List.of(
            "api_key", "api_secret", "otp", "pin", "access_token", "full_name", "phone_number", "email",
            "full_name_edit_counter", "mobile_number", "email_address", "email_preference", "authorization",
            "verified_token", "customer_phone_number", "x-api-secret", "x-api-key", "Authorization", "partner",
            "client_id", "public_key", "private_key", "x-public-key", "x-private-key", "newrelic");

    /**
     * Compiled once at class load. Building these per call meant recompiling
     * twenty-five regexes on every logged request.
     */
    private static final List<Pattern> REDACT_PATTERNS = REDACT_KEYS.stream()
            .map(key -> Pattern.compile(String.format("\"%s\"%s", key, REGEX_FILTER_KEY)))
            .toList();

    private Utils() {
    }

    public static String redact(@NonNull String string) {
        try {
            for (Pattern pattern : REDACT_PATTERNS) {
                Matcher matcher = pattern.matcher(string);
                if (matcher.find() && matcher.group(1) != null) {
                    String group = matcher.group(1);
                    if (!ObjectUtils.isEmpty(group.trim()) && !"\"\"".equals(group)) {
                        string = string.replace(group, MASK);
                    }
                }
            }
            return string;
        } catch (Exception e) {
            return string;
        }
    }
}

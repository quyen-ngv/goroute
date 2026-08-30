package com.ds.goroute.config.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared constant-time comparison for the shared secrets the filters check, so the two
 * of them cannot drift into different null handling.
 */
final class SecretComparison {

    private SecretComparison() {
    }

    /**
     * @param supplied the value the caller sent, which may be absent
     * @param expected the configured secret
     */
    static boolean matches(String supplied, String expected) {
        byte[] suppliedBytes = supplied == null
                ? new byte[0]
                : supplied.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expected == null
                ? new byte[0]
                : expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(suppliedBytes, expectedBytes);
    }
}

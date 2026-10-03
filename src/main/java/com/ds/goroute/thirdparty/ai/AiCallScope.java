package com.ds.goroute.thirdparty.ai;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Names the business purpose of the AI calls made inside {@link #call}, so the call log can tell
 * an itinerary ranking from a text moderation. Thread-bound: every AI client call is synchronous
 * on the caller's thread. Calls made outside a scope are logged with the client's defaults.
 */
public final class AiCallScope {
    public record Tag(String feature, String operation, UUID userId, String traceId) {
    }

    private static final ThreadLocal<Tag> CURRENT = new ThreadLocal<>();

    private AiCallScope() {
    }

    public static Tag current() {
        return CURRENT.get();
    }

    public static <T> T call(String feature, String operation, UUID userId, String traceId, Supplier<T> work) {
        Tag previous = CURRENT.get();
        CURRENT.set(new Tag(feature, operation, userId, traceId));
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}

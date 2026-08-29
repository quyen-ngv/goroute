package com.ds.goroute.service.moderation;

import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Carries flagged text from the point the request body is read to the point the saved
 * content gets an id.
 *
 * <p>A flag needs the id of the thing it is about, and that id does not exist yet while
 * the request body is being parsed. Rather than making every service remember to raise
 * its own flags after saving -- the exact kind of "someone will forget" the annotation
 * approach exists to avoid -- the verdicts are parked here and attached by
 * {@code ModerationResponseAdvice} once the response carries the new id.
 *
 * <p>Scoped to the request attributes, so it is naturally per-request and cleaned up with
 * it; no scoped-proxy configuration and no thread-local left behind by a pooled thread.
 */
public final class PendingModerationFlags {

    private static final String ATTRIBUTE = PendingModerationFlags.class.getName();

    /**
     * @param text the original text, kept so the reviewer sees what was written rather
     *             than only which term fired
     */
    public record Pending(ModeratedContentType contentType,
                          ModerationVisibility visibility,
                          String fieldLabel,
                          ModerationVerdict verdict,
                          String text) {
    }

    private PendingModerationFlags() {
    }

    public static void add(Pending pending) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Pending> existing = (List<Pending>) attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existing == null) {
            existing = new ArrayList<>();
            attributes.setAttribute(ATTRIBUTE, existing, RequestAttributes.SCOPE_REQUEST);
        }
        existing.add(pending);
    }

    /** Returns and clears everything parked for this request. */
    public static List<Pending> drain() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Pending> existing = (List<Pending>) attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existing == null || existing.isEmpty()) {
            return List.of();
        }
        attributes.removeAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return List.copyOf(existing);
    }
}

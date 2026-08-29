package com.ds.goroute.service;

import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import java.util.UUID;

/**
 * The single text filter for the whole product (MOD-03).
 *
 * <p>There is exactly one implementation and exactly one term list. No screen and no
 * service filters on its own; the app may run a quick local hint while the user types,
 * but the decision that counts is this one.
 */
public interface TextModerationService {

    /**
     * Evaluates one field, records the decision for MOD-08, and returns the verdict.
     * Never throws: the caller decides what a FLAG means for its own flow.
     */
    ModerationVerdict evaluate(ModeratedContentType contentType,
                               String fieldLabel,
                               ModerationVisibility visibility,
                               String text,
                               UUID userId);

    /**
     * Evaluates without recording anything. Used by the admin tester, which must let an
     * operator try a phrase against the current list before saving a change, and by the
     * client-side hint endpoint.
     */
    ModerationVerdict preview(String text, ModerationVisibility visibility);

    /**
     * The configured strictness of a visibility tier (MOD-07). Callers use it to decide
     * whether the slower AI layer should run at all.
     */
    com.ds.goroute.type.ModerationStrictness strictnessFor(ModerationVisibility visibility);

    /** Drops the cached term snapshot after an administrator edits the list. */
    void refresh();
}

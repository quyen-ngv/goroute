package com.ds.goroute.service;

import com.ds.goroute.type.ModerationCategory;

/**
 * Result of one image in a batch.
 *
 * <p>Per-image rather than per-batch on purpose (MOD-04): one rejected photo must not
 * fail the other five, and the caller has to be able to tell a rejection-for-content
 * apart from a technical failure, because only one of the two is worth retrying.
 *
 * @param url               where the stored image lives, {@code null} when not accepted
 * @param rejectedCategory  the policy group that caused a content rejection, {@code null}
 *                          when the image failed for a technical reason or succeeded
 * @param failureMessage    what to tell the user, {@code null} on success
 */
public record ImageUploadOutcome(String originalFilename,
                                 String url,
                                 ModerationCategory rejectedCategory,
                                 String failureMessage) {

    public static ImageUploadOutcome accepted(String originalFilename, String url) {
        return new ImageUploadOutcome(originalFilename, url, null, null);
    }

    public static ImageUploadOutcome rejectedByModeration(String originalFilename,
                                                          ModerationCategory category,
                                                          String message) {
        return new ImageUploadOutcome(originalFilename, null, category, message);
    }

    public static ImageUploadOutcome failed(String originalFilename, String message) {
        return new ImageUploadOutcome(originalFilename, null, null, message);
    }

    public boolean isAccepted() {
        return url != null;
    }

    /** True when retrying would produce exactly the same answer. */
    public boolean isContentRejection() {
        return rejectedCategory != null;
    }
}

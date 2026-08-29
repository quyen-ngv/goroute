package com.ds.goroute.dto.response;

import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.type.ModerationCategory;

/**
 * Per-file upload metadata for clients that support moderation-aware uploads.
 *
 * <p>The legacy upload URL remains in the response {@code data} field. This
 * object is deliberately exposed through a separate field so released clients
 * that deserialize {@code data} as {@code List<String>} remain compatible.</p>
 */
public record UploadedImageResponse(
        String originalFilename,
        String url,
        ModerationCategory rejectedCategory,
        String failureMessage,
        boolean contentRejection,
        boolean accepted) {

    public static UploadedImageResponse from(ImageUploadOutcome outcome) {
        return new UploadedImageResponse(
                outcome.originalFilename(),
                outcome.url(),
                outcome.rejectedCategory(),
                outcome.failureMessage(),
                outcome.isContentRejection(),
                outcome.isAccepted());
    }
}

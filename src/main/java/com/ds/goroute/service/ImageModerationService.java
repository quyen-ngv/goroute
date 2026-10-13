package com.ds.goroute.service;

import java.time.Duration;
import java.util.UUID;

/**
 * Content check for every image entering the product (MOD-04).
 *
 * <p>The check runs in the background, after the image is stored: calling the provider
 * inside the upload request added seconds to every photo. The trade-off is a short window
 * in which an image that will be blocked is already visible; a BLOCK closes it by hiding
 * the image everywhere ({@link HiddenImageService}) and telling the uploader, a FLAG puts
 * it in the review queue and leaves it up.
 */
public interface ImageModerationService {

    /**
     * Records a PENDING check for a stored image and schedules it. Returns at once; does
     * nothing when image moderation is switched off or no provider is configured.
     *
     * @param entryPoint which upload path this came from, so an administrator upload runs
     *                   one notch looser and the inventory of entry points is verifiable
     *                   from data rather than from a list
     */
    void submit(UUID userId, String entryPoint, String objectKey, String url,
                byte[] bytes, String contentType);

    /**
     * Re-runs checks still PENDING after {@code olderThan} -- the worker was lost to a
     * restart or a full pool -- reading the image back from storage.
     *
     * @return how many rows were picked up
     */
    int resumeStale(Duration olderThan, int limit);
}

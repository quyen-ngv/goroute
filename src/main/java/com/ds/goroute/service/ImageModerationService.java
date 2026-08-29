package com.ds.goroute.service;

import com.ds.goroute.service.moderation.ModerationVerdict;

import java.util.UUID;

/**
 * Content check for every image entering the product (MOD-04).
 *
 * <p>The check runs <em>before</em> the object is stored and before a URL exists. Storing
 * first and checking after leaves a window in which a rejected image is already
 * shareable, and for sexual or violent content minutes are already too late.
 */
public interface ImageModerationService {

    /**
     * Inspects raw image bytes.
     *
     * @param entryPoint which upload path this came from, so MOD-05 can tell an ordinary
     *                   user upload apart from an administrator one and so the inventory
     *                   of entry points is verifiable from data rather than from a list
     * @return BLOCK to reject the image, FLAG to accept it and queue it for a human,
     *         ALLOW otherwise
     */
    ModerationVerdict inspect(byte[] bytes, String contentType, UUID userId, String entryPoint);
}

package com.ds.goroute.service;

import com.ds.goroute.entity.ImageModerationResult;
import com.ds.goroute.type.ModerationCategory;

/**
 * Keeps images that moderation hid out of every API response.
 *
 * <p>An image URL is copied into whatever record it was uploaded for (a check-in, a review,
 * an avatar, a chat message...), so hiding it at each of those read paths would miss some.
 * Instead the set of hidden images is held in memory and the HTTP JSON writer swaps any
 * hidden URL for a neutral placeholder. Images are matched by file name, which is a random
 * UUID per upload, so the host or a query string on the URL does not matter.
 *
 * <p>The source of truth is {@code content_takedowns} with content type {@code IMAGE}; the
 * in-memory set is rebuilt from it with {@link #reload()}. Single-instance, like every
 * in-memory cache in this backend.
 */
public interface HiddenImageService {

    /**
     * Hides an image the background check blocked: records the takedown, takes it out of
     * responses immediately and tells the uploader.
     */
    void hide(ImageModerationResult image, ModerationCategory category);

    /** Rebuilds the in-memory set from the active image takedowns. */
    void reload();

    /** True when {@code value} is the URL (or object key) of a hidden image. */
    boolean isHidden(String value);

    /** The URL served in place of a hidden image. */
    String placeholderUrl();
}

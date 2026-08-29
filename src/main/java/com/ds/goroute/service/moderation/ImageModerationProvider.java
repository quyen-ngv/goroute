package com.ds.goroute.service.moderation;

import com.ds.goroute.type.ModerationCategory;

import java.util.Map;

/**
 * A recognition backend that scores an image per policy group.
 *
 * <p>Kept behind an interface because choosing the backend is an operational decision,
 * not an architectural one: the cloud service of the current infrastructure provider, a
 * self-hosted model and a vision-capable AI provider all fit here, and the criteria for
 * choosing between them -- accuracy on this product's own travel photos, latency, cost
 * per image and whether image data leaves the infrastructure -- can only be settled by
 * running them against real data.
 */
public interface ImageModerationProvider {

    /** Provider name recorded with each result, for later comparison. */
    String name();

    /**
     * Confidence per group, between 0 and 1. An empty map means "nothing detected"; a
     * thrown exception means the provider is unavailable and is handled by the caller.
     */
    Map<ModerationCategory, Double> score(byte[] bytes, String contentType);
}

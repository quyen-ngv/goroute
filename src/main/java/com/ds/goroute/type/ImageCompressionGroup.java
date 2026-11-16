package com.ds.goroute.type;

import java.util.Arrays;
import java.util.Optional;

/**
 * The storage folders whose images the console may compress (epic 00 §9b). Compression works on
 * the object under its key, so a group is a key prefix, not a table: every file under it is
 * covered whichever rows point at it. Folders left out on purpose: partner verification
 * documents ({@code partners/verification/}, licences and IDs must stay legible), and video,
 * audio and AR folders, which hold no photos.
 */
public enum ImageCompressionGroup {
    PLACES("places/", false),
    /**
     * Everything the app uploads through {@code POST /v1/api/files/upload}: check-in photos, trip
     * chat images, expense receipts, trip memories. The key is {@code expenses/<userId>/...}.
     */
    USER_UPLOADS("expenses/", true),
    AVATARS("avatars/", true),
    MARKETPLACE_CHAT("chat/", false),
    POST_MEDIA("post-media/", false),
    LOCATION_IMAGES("location-images/", false),
    CITY_STORIES("city-stories/", false),
    EVENTS("events/", false),
    ACTIVITIES("activities/", false),
    ACTIVITY_BOOKINGS("bookings/", false),
    FOODS("foods/", false),
    PLACE_REVIEWS("reviews/", false),
    MARKETPLACE("marketplace/", false),
    PARTNER_ONBOARDING("partners/onboarding/", false),
    ADMIN_MEDIA("admin-media/", false),
    ASSET_LIBRARY("asset-library/", false),
    PASSPORT_CATALOG("passport-catalog/", false);

    private final String prefix;
    /** The second key segment is the uploading user's id, so the FREE/PRO tier can be looked up. */
    private final boolean ownerInKey;

    ImageCompressionGroup(String prefix, boolean ownerInKey) {
        this.prefix = prefix;
        this.ownerInKey = ownerInKey;
    }

    public String prefix() {
        return prefix;
    }

    public boolean ownerInKey() {
        return ownerInKey;
    }

    /** The group a key belongs to; empty for keys outside every group (never compressed). */
    public static Optional<ImageCompressionGroup> forKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(group -> key.startsWith(group.prefix)).findFirst();
    }
}

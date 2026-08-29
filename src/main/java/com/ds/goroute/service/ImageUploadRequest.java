package com.ds.goroute.service;

import java.util.UUID;

/**
 * Describes one image entry point (MOD-05).
 *
 * <p>Every path that puts an image into the product goes through the upload service and
 * says who it is. Naming the entry point is what makes the inventory of entry points
 * checkable from data instead of from a list somebody has to keep up to date, and it is
 * what lets an administrator path run at a relaxed level while still going through the
 * check.
 *
 * @param userId      who is uploading, {@code null} for system paths
 * @param entryPoint  short stable identifier, recorded with the moderation result;
 *                    see {@link ImageEntryPoint}
 * @param objectPrefix storage prefix, for example {@code avatars/<userId>}
 * @param compress    whether to run the image through the compression service first
 */
public record ImageUploadRequest(UUID userId,
                                 String entryPoint,
                                 String objectPrefix,
                                 boolean compress) {

    public static ImageUploadRequest of(UUID userId, String entryPoint, String objectPrefix) {
        return new ImageUploadRequest(userId, entryPoint, objectPrefix, true);
    }

    /**
     * The complete inventory of image entry points. Adding a constant here is the visible
     * cost of adding a new way for images to enter the product, and every one of them is
     * routed through the same door.
     */
    public static final class ImageEntryPoint {
        /** Ordinary users: expense receipts and generic uploads. */
        public static final String USER_UPLOAD = "user-upload";
        /** Ordinary users: profile pictures, shown on every review, comment and feed row. */
        public static final String USER_AVATAR = "user-avatar";
        /** Ordinary users: check-in photos. */
        public static final String CHECKIN_PHOTO = "checkin-photo";
        /** Partner staff: hotel media. */
        public static final String PARTNER_HOTEL = "partner-hotel";
        /** Partner staff: activity media. */
        public static final String PARTNER_ACTIVITY = "partner-activity";
        /** Operators: media library, direct upload. */
        public static final String ADMIN_MEDIA = "admin-media";
        /** Operators: media library, fetched from an external address. */
        public static final String ADMIN_MEDIA_FROM_URL = "admin-media-from-url";
        /** Operators and system: curated location imagery. */
        public static final String LOCATION_IMAGE = "admin-location-image";
        /** Guide applicants: profile and identity documents. */
        public static final String GUIDE_PROFILE = "guide-profile";

        private ImageEntryPoint() {
        }
    }
}

package com.ds.goroute.quest.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * An AR object (§3.15) as the player's app needs it: the object and its library asset in one, so
 * the app can download the model and place it without a second lookup. Both model files are listed;
 * the app downloads only the one its platform shows (GLB on Android, USDZ on iOS).
 *
 * <p>In a run, {@code description}, {@code imageUrls} and the recording arrive only once the object
 * is tapped. In a pack (local-first play) everything is there, as with the rest of the pack.
 */
public record QuestArObjectView(
        UUID assetId,
        String assetName,
        String glbUrl,
        String usdzUrl,
        String thumbnailUrl,
        BigDecimal heightM,
        /** The model's clips in timeline order; iOS cuts them from the USDZ's single timeline. */
        List<Clip> clips,
        boolean canWander,
        String behavior,
        String anchorMode,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer headingDeg,
        int spawnRadiusM,
        Integer wanderRadiusM,
        /** How near the object a tap counts, in metres (the server's AR_INTERACT_RADIUS_METERS). */
        int interactRadiusM,
        BigDecimal scale,
        List<Marker> markers,
        String title,
        String description,
        List<String> imageUrls,
        String audioUrl,
        Integer audioSeconds,
        /** Metres above the ground (APPROX); null on the ground. */
        BigDecimal elevationM) {

    public record Clip(String name, double seconds) {
    }

    /** A landmark: its photo and real width, and where the object stands in its frame. */
    public record Marker(String imageUrl, BigDecimal widthM, BigDecimal offsetX, BigDecimal offsetY,
                         BigDecimal offsetZ, BigDecimal yawDeg) {
    }
}

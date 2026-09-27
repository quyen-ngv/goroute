package com.ds.goroute.quest.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A checkpoint's AR object (§3.15), stored whole in {@code quest_checkpoints.ar_object}.
 *
 * <p>{@code latitude}/{@code longitude} is where the object stands (FIXED) or the centre of its zone
 * (WANDER). It may sit apart from the checkpoint's own spot. {@code spawnRadiusM} is how near a
 * player must be for it to show on the map; a tap counts within the configured interact radius of
 * it (plus {@code wanderRadiusM} for a wandering one).
 *
 * <p>An IMAGE-anchored object carries its landmarks: the photo, its real width, and where the object
 * stands in the landmark's own frame. That frame is the same on ARKit and ARCore: x along the
 * image's width, z along its height (towards the bottom edge), y out of the image. The object stays
 * upright whatever the landmark's tilt; {@code yawDeg} turns it about the vertical.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestArObject(
        UUID assetId,
        String behavior,
        String anchorMode,
        BigDecimal latitude,
        BigDecimal longitude,
        /** APPROX: the direction the object faces, degrees clockwise from north. */
        Integer headingDeg,
        Integer spawnRadiusM,
        /** WANDER only: how far from its centre it roams on the map, in metres. */
        Integer wanderRadiusM,
        /** Multiplies the asset's authored size; 1 shows it at real size. */
        BigDecimal scale,
        List<Marker> markers,
        String title,
        String description,
        List<String> imageUrls,
        String audioUrl,
        Integer audioSeconds) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Marker(String imageUrl, BigDecimal widthM, Vec3 offset, BigDecimal yawDeg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vec3(BigDecimal x, BigDecimal y, BigDecimal z) {
    }

    public List<Marker> markersOrEmpty() {
        return markers == null ? List.of() : markers;
    }

    public List<String> imageUrlsOrEmpty() {
        return imageUrls == null ? List.of() : imageUrls;
    }
}

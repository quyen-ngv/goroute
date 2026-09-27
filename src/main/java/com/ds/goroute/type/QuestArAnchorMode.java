package com.ds.goroute.type;

/**
 * Where an AR object (§3.15) appears. {@code APPROX} places it on the ground in the direction of its
 * coordinates, as close as GPS allows; {@code IMAGE} ties it to a landmark the creator photographed
 * on site, so it appears exactly where they put it. Both work offline, on the phone alone.
 */
public enum QuestArAnchorMode {
    APPROX,
    IMAGE
}

package com.ds.goroute.type;

/**
 * How an AR object (§3.15) behaves. {@code FIXED} stands still where the creator put it;
 * {@code WANDER} moves around the player in AR, and on the map is only a zone. Only an asset with a
 * {@code walk} clip may wander.
 */
public enum QuestArBehavior {
    FIXED,
    WANDER
}

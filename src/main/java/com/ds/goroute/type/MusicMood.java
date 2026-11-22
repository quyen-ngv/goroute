package com.ds.goroute.type;

/**
 * The mood a background-music track is filed under in the app's picker.
 *
 * <p>Stored by name in {@code music_tracks.mood}, which a CHECK constraint (V282) limits to
 * these names: adding one means a migration as well.
 */
public enum MusicMood {
    CHILL,
    HAPPY,
    UPLIFTING,
    EPIC,
    GROOVY
}

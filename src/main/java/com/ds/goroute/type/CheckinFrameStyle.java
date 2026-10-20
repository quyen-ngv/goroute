package com.ds.goroute.type;

/**
 * The frame a check-in's photos are shown in. The app draws the frames; the server only
 * keeps which one the author chose. A check-in with none shows its photos plainly.
 *
 * <p>Stored by name in {@code user_checkins.frame_style}, which a CHECK constraint (V243)
 * limits to these names: adding one means a migration as well.
 */
public enum CheckinFrameStyle {
    AIRPLANE_WINDOW,
    FILM_STRIP,
    STAMP,
    /** An air-mail envelope with the post office's date stamp. */
    POSTMARK,
    TRAIN_WINDOW
}

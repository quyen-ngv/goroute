package com.ds.goroute.type;

/**
 * The five question shapes at v1 (§3.12). {@code ARRIVE_ONLY} was dropped: "just arrive" is no
 * longer a valid checkpoint (D21) &mdash; a checkpoint with no question must instead require a
 * check-in.
 */
public enum QuestQuestionType {
    /** A short word or phrase, normalised (lowercase, Vietnamese diacritics stripped) then matched against variants. */
    TEXT,
    /** A number, matched exactly or within an optional tolerance. */
    NUMBER,
    /** One of 3–5 options, matched by choice id (order is shuffled each run). */
    CHOICE,
    /** All correct options must be selected; matched as a set. */
    MULTI_CHOICE,
    /** A photo taken on the spot. Always scored correct (D12) but must come from the camera, not the gallery. */
    PHOTO;

    public boolean isChoiceBased() {
        return this == CHOICE || this == MULTI_CHOICE;
    }

    /** PHOTO always scores correct; the guard the player must clear is that it is a live camera capture. */
    public boolean isAlwaysCorrect() {
        return this == PHOTO;
    }

    public boolean requiresCameraCapture() {
        return this == PHOTO;
    }
}

package com.ds.goroute.type;

/**
 * Where a checkpoint's coordinates came from (§6.2). A checkpoint pinned on a map is {@code MAP};
 * one whose coordinates were recorded standing on the spot is {@code FIELD}. Submitting a quest
 * for review requires every checkpoint to be {@code FIELD} (§7.3) &mdash; the one guard left in
 * place of a mandatory field test.
 */
public enum QuestCaptureSource {
    MAP,
    FIELD
}

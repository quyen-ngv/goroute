package com.ds.goroute.type;

/**
 * A finding clue on an AREA checkpoint (§3.14.1). {@code REVEAL} hands the player the real pin; a
 * checkpoint has at most one and it is always the last tier.
 */
public enum QuestClueKind {
    TEXT,
    PHOTO,
    REVEAL
}

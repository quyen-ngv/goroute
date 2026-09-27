package com.ds.goroute.type;

/**
 * How a player finds a checkpoint (§3.14.1). {@code PIN} shows the pin and its unlock circle;
 * {@code AREA} shows only a search circle offset from the real spot, and the player buys finding
 * clues until they find it. Unlocking an AREA checkpoint means entering the search circle.
 */
public enum QuestFindMode {
    PIN,
    AREA;

    /** The stored value, defaulting an unset or unknown one to {@link #PIN} (how rows played before). */
    public static QuestFindMode of(String value) {
        return "AREA".equals(value) ? AREA : PIN;
    }
}

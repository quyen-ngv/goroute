package com.ds.goroute.type;

/**
 * What clears a checkpoint once it is unlocked (§3.14). Every mode also requires the checkpoint
 * to be unlocked first.
 *
 * <ul>
 *   <li>{@code TASK}: every required question answered and the required check-in done (D21).</li>
 *   <li>{@code ARRIVE}: a guide-only checkpoint; unlocking is enough.</li>
 *   <li>{@code STOPS}: at least {@code min_stops} storytelling points heard by GPS.</li>
 * </ul>
 */
public enum QuestCompletionMode {
    TASK,
    ARRIVE,
    STOPS;

    /** The stored value, defaulting an unset or unknown one to {@link #TASK}. */
    public static QuestCompletionMode of(String value) {
        if ("ARRIVE".equals(value)) {
            return ARRIVE;
        }
        return "STOPS".equals(value) ? STOPS : TASK;
    }
}

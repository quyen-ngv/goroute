package com.ds.goroute.type;

/**
 * What clears a checkpoint once it is unlocked (§3.14). Every mode also requires the checkpoint
 * to be unlocked first.
 *
 * <ul>
 *   <li>{@code TASK}: every required question answered and the required check-in done (D21).</li>
 *   <li>{@code ARRIVE}: a guide-only checkpoint; unlocking is enough.</li>
 *   <li>{@code STOPS}: at least {@code min_stops} storytelling points heard by GPS.</li>
 *   <li>{@code AR_OBJECT}: the checkpoint's AR object found and tapped near where it stands (§3.15).
 *       A member whose app cannot show AR plays it as {@code ARRIVE}.</li>
 * </ul>
 */
public enum QuestCompletionMode {
    TASK,
    ARRIVE,
    STOPS,
    AR_OBJECT;

    /** The stored value, defaulting an unset or unknown one to {@link #TASK}. */
    public static QuestCompletionMode of(String value) {
        if ("ARRIVE".equals(value)) {
            return ARRIVE;
        }
        if ("AR_OBJECT".equals(value)) {
            return AR_OBJECT;
        }
        return "STOPS".equals(value) ? STOPS : TASK;
    }
}

package com.ds.goroute.type;

import java.util.EnumSet;
import java.util.Set;

/**
 * The lifecycle of a single play-through (§6.2) — a different axis from {@link QuestStatus}, which
 * is the content's lifecycle. A run pins a {@code quest_version_id} at start (D11), so a quest
 * edited or paused mid-play does not change the run in progress.
 *
 * <ul>
 *   <li>{@code IN_PROGRESS} — the only non-terminal state.</li>
 *   <li>{@code COMPLETED} — every gate cleared; rewards granted.</li>
 *   <li>{@code ABANDONED} — the player gave up (explicitly or via idle timeout).</li>
 *   <li>{@code EXPIRED} — the run's own clock ran out ({@code run_expiry}).</li>
 *   <li>{@code TERMINATED} — the quest was suspended under the player; Stars are refunded (§6.3).</li>
 * </ul>
 */
public enum QuestRunStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED,
    EXPIRED,
    TERMINATED;

    private static final Set<QuestRunStatus> TERMINAL =
            EnumSet.of(COMPLETED, ABANDONED, EXPIRED, TERMINATED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isActive() {
        return this == IN_PROGRESS;
    }
}

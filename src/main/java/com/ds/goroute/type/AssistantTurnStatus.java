package com.ds.goroute.type;

/**
 * Lifecycle of one question to the chat assistant (Pengoo).
 *
 * <pre>
 * QUEUED ──► RUNNING ──► COMPLETED
 *    │          └──────► FAILED
 *    └─────────────────► FAILED
 * REJECTED: refused up front (daily allowance used up); never runs.
 * </pre>
 *
 * Only one turn per conversation is RUNNING; later questions wait as QUEUED.
 */
public enum AssistantTurnStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    REJECTED;

    public boolean isOpen() {
        return this == QUEUED || this == RUNNING;
    }
}

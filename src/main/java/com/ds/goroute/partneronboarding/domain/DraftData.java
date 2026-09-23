package com.ds.goroute.partneronboarding.domain;

import java.util.Collections;
import java.util.Map;

/**
 * Read-only view over a draft's answers, keyed by step code.
 *
 * <p>Materializers read a lot of loosely typed JSON. Without this they would each repeat the
 * same casts, null checks and "which step was that in again" lookups, and the first one to
 * forget a check would throw {@link ClassCastException} out of a submit. So the casting
 * lives here once, a missing step reads as an empty step rather than null, and a value that
 * is required says which step and field it came from when it is absent.
 */
public final class DraftData {

    private final Map<String, Object> steps;

    private DraftData(Map<String, Object> steps) {
        this.steps = steps;
    }

    public static DraftData of(Map<String, Object> raw) {
        return new DraftData(raw == null ? Collections.emptyMap() : raw);
    }

    /** Answers of one step; never null, so callers can chain straight into a default. */
    public StepData step(String stepCode) {
        Object value = steps.get(stepCode);
        return StepData.of(stepCode, value);
    }

    public boolean hasStep(String stepCode) {
        return !step(stepCode).isEmpty();
    }
}

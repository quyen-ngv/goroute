package com.ds.goroute.type;

/**
 * A reviewer's decision on a quest in review (§3.1). Each maps to the status the quest moves to,
 * and the decision to {@code PUBLISHED} is itself the publish action — there is no separate step.
 */
public enum QuestReviewDecision {
    PUBLISHED,
    DENIED,
    FIELD_TEST;

    public QuestStatus targetStatus() {
        return QuestStatus.valueOf(name());
    }
}

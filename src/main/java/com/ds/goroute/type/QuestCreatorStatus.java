package com.ds.goroute.type;

/**
 * The state of a {@code quest_creators} profile. The profile is a record, not a licence: it is
 * created the first time a user taps "create quest" (§6.2). {@code SUSPENDED} takes a creator's
 * quests out of discovery and blocks new submissions (§3.2).
 */
public enum QuestCreatorStatus {
    ACTIVE,
    SUSPENDED
}

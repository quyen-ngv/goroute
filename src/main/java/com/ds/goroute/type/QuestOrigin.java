package com.ds.goroute.type;

/**
 * How a quest was born, fixed at creation and never changed (D16). A {@code SYSTEM} quest is the
 * only kind a {@code SUPER_ADMIN} may self-approve (§3.3 layer 2); {@code PARTNER} and
 * {@code COMMUNITY} quests always need a reviewer other than their creator.
 */
public enum QuestOrigin {
    SYSTEM,
    PARTNER,
    COMMUNITY
}

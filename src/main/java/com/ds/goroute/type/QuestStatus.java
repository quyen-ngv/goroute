package com.ds.goroute.type;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The quest lifecycle (§3.1). The review path is four states &mdash;
 * {@code DRAFT → PENDING → IN_REVIEW → PUBLISHED | DENIED} &mdash; and the publish gate is the
 * review decision itself, not a separate step. {@code FIELD_TEST} is an optional detour the
 * reviewer may take; {@code PAUSED} / {@code SUSPENDED} / {@code ARCHIVED} are the operational
 * off-states.
 *
 * <p>Who may drive which transition is part of the contract, encoded here rather than scattered
 * across services, and mirrored to the console and the app. Two guards depend on data the enum
 * cannot see and stay in the service layer: {@code PAUSED → PUBLISHED} is a creator's right only
 * when that same creator paused it (auto-pause by {@code SYSTEM} is admin-only to lift), and
 * {@code ARCHIVED → PUBLISHED} is allowed only inside the restore window.
 */
public enum QuestStatus {
    DRAFT,
    PENDING,
    IN_REVIEW,
    FIELD_TEST,
    PUBLISHED,
    DENIED,
    PAUSED,
    SUSPENDED,
    ARCHIVED;

    /** Who is driving a transition. */
    public enum Actor { CREATOR, ADMIN, SYSTEM }

    private static final Map<QuestStatus, Map<QuestStatus, Set<Actor>>> TRANSITIONS =
            new EnumMap<>(QuestStatus.class);

    private static void allow(QuestStatus from, QuestStatus to, Actor... actors) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(QuestStatus.class))
                .put(to, EnumSet.copyOf(Set.of(actors)));
    }

    static {
        allow(DRAFT, PENDING, Actor.CREATOR, Actor.ADMIN);
        allow(PENDING, IN_REVIEW, Actor.ADMIN, Actor.SYSTEM);
        allow(IN_REVIEW, PUBLISHED, Actor.ADMIN);
        allow(IN_REVIEW, DENIED, Actor.ADMIN);
        allow(IN_REVIEW, FIELD_TEST, Actor.ADMIN);
        allow(FIELD_TEST, PUBLISHED, Actor.ADMIN);
        allow(FIELD_TEST, DENIED, Actor.ADMIN);
        // DENIED is not a dead end: the creator reopening a denied quest sends it back to DRAFT.
        allow(DENIED, DRAFT, Actor.CREATOR, Actor.SYSTEM);
        allow(PUBLISHED, PAUSED, Actor.CREATOR, Actor.ADMIN, Actor.SYSTEM);
        allow(PUBLISHED, SUSPENDED, Actor.ADMIN);
        allow(PUBLISHED, ARCHIVED, Actor.CREATOR, Actor.ADMIN);
        allow(PAUSED, PUBLISHED, Actor.CREATOR, Actor.ADMIN);
        allow(PAUSED, SUSPENDED, Actor.ADMIN);
        allow(PAUSED, ARCHIVED, Actor.CREATOR, Actor.ADMIN);
        allow(SUSPENDED, PUBLISHED, Actor.ADMIN);
        allow(SUSPENDED, ARCHIVED, Actor.ADMIN);
        allow(ARCHIVED, PUBLISHED, Actor.ADMIN);
    }

    /** The actors permitted to move from this status to {@code target}, ignoring data-dependent guards. */
    public Set<Actor> actorsFor(QuestStatus target) {
        return TRANSITIONS.getOrDefault(this, Map.of()).getOrDefault(target, Set.of());
    }

    public boolean canTransitionTo(QuestStatus target, Actor actor) {
        return actorsFor(target).contains(actor);
    }

    /** Whether any actor may make this transition. */
    public boolean canTransitionTo(QuestStatus target) {
        return !actorsFor(target).isEmpty();
    }

    /** The statuses a creator may move a quest into from here. */
    public Set<QuestStatus> targetsForCreator() {
        return targetsFor(Actor.CREATOR);
    }

    public Set<QuestStatus> targetsForAdmin() {
        return targetsFor(Actor.ADMIN);
    }

    private Set<QuestStatus> targetsFor(Actor actor) {
        Set<QuestStatus> out = EnumSet.noneOf(QuestStatus.class);
        for (var entry : TRANSITIONS.getOrDefault(this, Map.of()).entrySet()) {
            if (entry.getValue().contains(actor)) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    /** Only {@code PUBLISHED} quests are discoverable by strangers (§6.3). */
    public boolean isPubliclyDiscoverable() {
        return this == PUBLISHED;
    }

    /** DRAFT and DENIED are the states a creator may edit content in. */
    public boolean isCreatorEditable() {
        return this == DRAFT || this == DENIED;
    }

    /** SUSPENDED is the emergency takedown: the creator cannot touch it, only an admin lifts it. */
    public boolean isEmergencyTakedown() {
        return this == SUSPENDED;
    }
}

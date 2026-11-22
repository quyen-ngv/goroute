package com.ds.goroute.type;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The stored meetup lifecycle (SPEC_V2 §2.2). Every meetup is reviewed before anyone else sees
 * it, and nothing is edited after approval: a host who wants a change cancels and creates again.
 *
 * <pre>
 * (create, HOST)  → PENDING_REVIEW
 * PENDING_REVIEW  → PENDING_REVIEW (HOST edit, revision++) | APPROVED | REJECTED (ADMIN) | WITHDRAWN (HOST)
 * REJECTED        → PENDING_REVIEW (HOST edit and resubmit, while resubmits are left)
 * APPROVED        → CANCELLED (HOST) | REMOVED (ADMIN)
 * </pre>
 *
 * <p>Being past ({@code visible_until <= now}) is computed, never stored; see
 * {@link MeetupEffectiveStatus}. The data-dependent guards (not past, resubmits left, expected
 * version) stay in the service.
 */
public enum MeetupStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    CANCELLED,
    REMOVED;

    /** Who is driving a transition. */
    public enum Actor { HOST, ADMIN, SYSTEM }

    private static final Map<MeetupStatus, Map<MeetupStatus, Set<Actor>>> TRANSITIONS =
            new EnumMap<>(MeetupStatus.class);

    private static void allow(MeetupStatus from, MeetupStatus to, Actor... actors) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(MeetupStatus.class))
                .put(to, EnumSet.copyOf(Set.of(actors)));
    }

    static {
        allow(PENDING_REVIEW, PENDING_REVIEW, Actor.HOST);
        allow(PENDING_REVIEW, APPROVED, Actor.ADMIN);
        allow(PENDING_REVIEW, REJECTED, Actor.ADMIN);
        allow(PENDING_REVIEW, WITHDRAWN, Actor.HOST);
        allow(REJECTED, PENDING_REVIEW, Actor.HOST);
        allow(APPROVED, CANCELLED, Actor.HOST);
        allow(APPROVED, REMOVED, Actor.ADMIN);
    }

    /** The status every new meetup starts in: it is reviewed before anyone else sees it. */
    public static MeetupStatus initial() {
        return PENDING_REVIEW;
    }

    /** The actors permitted to move from this status to {@code target}, ignoring data-dependent guards. */
    public Set<Actor> actorsFor(MeetupStatus target) {
        return TRANSITIONS.getOrDefault(this, Map.of()).getOrDefault(target, Set.of());
    }

    public boolean canTransitionTo(MeetupStatus target, Actor actor) {
        return actorsFor(target).contains(actor);
    }

    /** PENDING_REVIEW and REJECTED are the states a host may edit (REJECTED as a resubmit). */
    public boolean isHostEditable() {
        return canTransitionTo(PENDING_REVIEW, Actor.HOST);
    }

    /** Only an approved meetup is ever shown to someone other than its host. */
    public boolean isPubliclyVisible() {
        return this == APPROVED;
    }

    /** Statuses that count against MEETUP/MAX_ACTIVE_PER_HOST while not past. */
    public boolean isActive() {
        return this == PENDING_REVIEW || this == APPROVED;
    }
}

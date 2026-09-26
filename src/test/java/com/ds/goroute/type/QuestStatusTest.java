package com.ds.goroute.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ds.goroute.type.QuestStatus.Actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("QuestStatus")
class QuestStatusTest {

    @Test
    @DisplayName("the four-state review path is walkable by the right actors")
    void reviewPath() {
        assertTrue(QuestStatus.DRAFT.canTransitionTo(QuestStatus.PENDING, Actor.CREATOR));
        assertTrue(QuestStatus.DRAFT.canTransitionTo(QuestStatus.PENDING, Actor.ADMIN));
        // The admin opens a pending quest for review; the system also flips it on open.
        assertTrue(QuestStatus.PENDING.canTransitionTo(QuestStatus.IN_REVIEW, Actor.ADMIN));
        assertTrue(QuestStatus.PENDING.canTransitionTo(QuestStatus.IN_REVIEW, Actor.SYSTEM));
        assertFalse(QuestStatus.PENDING.canTransitionTo(QuestStatus.IN_REVIEW, Actor.CREATOR));
        assertTrue(QuestStatus.IN_REVIEW.canTransitionTo(QuestStatus.PUBLISHED, Actor.ADMIN));
        assertTrue(QuestStatus.IN_REVIEW.canTransitionTo(QuestStatus.DENIED, Actor.ADMIN));
        assertTrue(QuestStatus.IN_REVIEW.canTransitionTo(QuestStatus.FIELD_TEST, Actor.ADMIN));
    }

    @Test
    @DisplayName("field test is optional and only an admin drives it")
    void fieldTestIsAdminOnly() {
        assertTrue(QuestStatus.FIELD_TEST.canTransitionTo(QuestStatus.PUBLISHED, Actor.ADMIN));
        assertTrue(QuestStatus.FIELD_TEST.canTransitionTo(QuestStatus.DENIED, Actor.ADMIN));
        assertFalse(QuestStatus.FIELD_TEST.canTransitionTo(QuestStatus.PUBLISHED, Actor.CREATOR));
    }

    @Test
    @DisplayName("DENIED is not a dead end: the creator reopens it back to DRAFT")
    void deniedReopensToDraft() {
        assertTrue(QuestStatus.DENIED.canTransitionTo(QuestStatus.DRAFT, Actor.CREATOR));
        assertTrue(QuestStatus.DENIED.canTransitionTo(QuestStatus.DRAFT, Actor.SYSTEM));
        assertFalse(QuestStatus.DENIED.canTransitionTo(QuestStatus.PUBLISHED, Actor.ADMIN));
    }

    @Test
    @DisplayName("only an admin may suspend, and the creator can never touch a suspended quest")
    void suspensionIsAdminOnly() {
        assertTrue(QuestStatus.PUBLISHED.canTransitionTo(QuestStatus.SUSPENDED, Actor.ADMIN));
        assertTrue(QuestStatus.PAUSED.canTransitionTo(QuestStatus.SUSPENDED, Actor.ADMIN));
        assertFalse(QuestStatus.PUBLISHED.canTransitionTo(QuestStatus.SUSPENDED, Actor.CREATOR));
        assertFalse(QuestStatus.PUBLISHED.canTransitionTo(QuestStatus.SUSPENDED, Actor.SYSTEM));
        // Only the admin lifts a suspension.
        assertTrue(QuestStatus.SUSPENDED.canTransitionTo(QuestStatus.PUBLISHED, Actor.ADMIN));
        assertTrue(QuestStatus.SUSPENDED.canTransitionTo(QuestStatus.ARCHIVED, Actor.ADMIN));
        assertFalse(QuestStatus.SUSPENDED.canTransitionTo(QuestStatus.PUBLISHED, Actor.CREATOR));
        assertTrue(QuestStatus.SUSPENDED.isEmergencyTakedown());
    }

    @Test
    @DisplayName("the system may auto-pause a published quest but not resume it")
    void autoPauseIsOneWayForTheSystem() {
        assertTrue(QuestStatus.PUBLISHED.canTransitionTo(QuestStatus.PAUSED, Actor.SYSTEM));
        assertTrue(QuestStatus.PUBLISHED.canTransitionTo(QuestStatus.PAUSED, Actor.CREATOR));
        assertFalse(QuestStatus.PAUSED.canTransitionTo(QuestStatus.PUBLISHED, Actor.SYSTEM));
        // The enum lets a creator resume; the "only if the creator paused it" guard is a service check.
        assertTrue(QuestStatus.PAUSED.canTransitionTo(QuestStatus.PUBLISHED, Actor.CREATOR));
        assertTrue(QuestStatus.PAUSED.canTransitionTo(QuestStatus.PUBLISHED, Actor.ADMIN));
    }

    @Test
    @DisplayName("discovery and edit helpers")
    void helpers() {
        assertTrue(QuestStatus.PUBLISHED.isPubliclyDiscoverable());
        assertFalse(QuestStatus.PAUSED.isPubliclyDiscoverable());
        assertFalse(QuestStatus.DRAFT.isPubliclyDiscoverable());
        assertTrue(QuestStatus.DRAFT.isCreatorEditable());
        assertTrue(QuestStatus.DENIED.isCreatorEditable());
        assertFalse(QuestStatus.PUBLISHED.isCreatorEditable());
    }

    @Test
    @DisplayName("terminal states go nowhere further on their own path")
    void archivedRestoreIsAdminOnly() {
        assertTrue(QuestStatus.ARCHIVED.canTransitionTo(QuestStatus.PUBLISHED, Actor.ADMIN));
        assertFalse(QuestStatus.ARCHIVED.canTransitionTo(QuestStatus.PUBLISHED, Actor.CREATOR));
        // A creator may archive their own published/paused quest (subject to service rules).
        assertTrue(QuestStatus.PUBLISHED.canTransitionTo(QuestStatus.ARCHIVED, Actor.CREATOR));
        assertTrue(QuestStatus.PAUSED.canTransitionTo(QuestStatus.ARCHIVED, Actor.CREATOR));
    }

    @Test
    @DisplayName("nine states, and no accidental self-transition")
    void invariants() {
        assertEquals(9, QuestStatus.values().length);
        for (QuestStatus status : QuestStatus.values()) {
            assertFalse(status.canTransitionTo(status), status + " should not transition to itself");
        }
    }
}

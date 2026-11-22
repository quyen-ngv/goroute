package com.ds.goroute.service.expense;

import java.util.UUID;

/**
 * One person in a ledger: a registered user, or a trip guest ({@code trip_members.id} of an
 * {@code is_guest} row). Exactly one of the two ids is set; equality is by that id and its kind,
 * so a guest and a user never collide even if the UUIDs did.
 */
public record ParticipantRef(UUID userId, UUID guestMemberId) {

    public ParticipantRef {
        if ((userId == null) == (guestMemberId == null)) {
            throw new IllegalArgumentException("A participant is either a user or a guest");
        }
    }

    public static ParticipantRef user(UUID userId) {
        return new ParticipantRef(userId, null);
    }

    public static ParticipantRef guest(UUID guestMemberId) {
        return new ParticipantRef(null, guestMemberId);
    }

    /** The participant named by these columns: the user when set, else the guest, else null. */
    public static ParticipantRef of(UUID userId, UUID guestMemberId) {
        if (userId != null) return user(userId);
        if (guestMemberId != null) return guest(guestMemberId);
        return null;
    }

    public boolean isGuest() {
        return guestMemberId != null;
    }

    /** A stable text key ("u:…" or "g:…"), used for deterministic ordering. */
    public String key() {
        return userId != null ? "u:" + userId : "g:" + guestMemberId;
    }
}

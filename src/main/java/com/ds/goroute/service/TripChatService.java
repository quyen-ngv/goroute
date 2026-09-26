package com.ds.goroute.service;

import com.ds.goroute.dto.response.MarketplaceConversationResponse;

import java.util.UUID;

/**
 * The group chat every trip has.
 *
 * <p>Membership is not a second list to keep: it is the trip's own member list, mirrored
 * into the conversation so that read markers, mentions and notifications have rows to hang
 * off. The mirror is reconciled whenever the trip's membership changes and again whenever
 * somebody opens the thread, and the question "may this person read it" is answered from the
 * trip rather than from the mirror -- so a removed member loses the chat immediately, not at
 * the next reconcile.
 */
public interface TripChatService {

    /**
     * Opens (creating on first use) the trip's conversation for one member.
     *
     * <p>Created lazily rather than backfilled: a trip that nobody chats in never needs a
     * conversation row, and one created three years ago works the first time it is opened.
     */
    MarketplaceConversationResponse open(UUID actor, UUID tripId);

    /** Gives a brand new trip its group chat, so the first member to look finds a thread. */
    void onTripCreated(UUID tripId);

    /**
     * Reconciles the conversation with the trip's members after a join, a leave, a removal
     * or a guest being linked to an account. Does nothing when the trip has no conversation
     * yet, because there is then nothing to be out of date.
     */
    void onMembershipChanged(UUID tripId);

    /** Retires the conversation with its trip; the transcript stays for the people in it. */
    void onTripDeleted(UUID tripId);
}

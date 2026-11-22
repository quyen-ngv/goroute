package com.ds.goroute.meetup.service;

import com.ds.goroute.dto.response.MarketplaceConversationResponse;

import java.util.UUID;

/**
 * The group chat every meetup has.
 *
 * <p>Its members are not a list of their own: they are the host plus every participant whose
 * row is {@code JOINED}, mirrored into the conversation so read markers and notifications have
 * rows to hang off. Whether somebody may read the thread is asked of the meetup, live (see
 * {@code MarketplaceChatRepository.isMeetupHostOrJoined}), so leaving or being removed takes
 * the chat away at once; the mirror is reconciled after every seat change and on every open.
 */
public interface MeetupChatService {

    /**
     * Opens (creating on first use) the meetup's conversation for its host or a JOINED
     * participant; anyone else is refused with 403.
     */
    MarketplaceConversationResponse open(UUID actor, UUID meetupId);

    /** Gives a brand new meetup its group chat, with the host as its only member. */
    void onMeetupCreated(UUID meetupId);

    /**
     * Reconciles the conversation after a seat changed (joined, accepted, left, removed) and
     * announces arrivals and departures in the thread. Does nothing when the meetup has no
     * conversation yet.
     */
    void onMembershipChanged(UUID meetupId);

    /** Retires the conversation when the meetup ends early (withdrawn, cancelled, removed). */
    void onMeetupClosed(UUID meetupId);
}

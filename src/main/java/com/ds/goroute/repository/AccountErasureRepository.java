package com.ds.goroute.repository;

import java.util.List;
import java.util.UUID;

/**
 * The bulk statements of account erasure (AccountErasureService). An exception to "one repository
 * per table": erasure is the one use case that touches every table holding a person's private
 * data, each by owner id only and with no branching, and spreading it over twenty repositories
 * would hide which rows an erased account loses.
 */
public interface AccountErasureRepository {

    List<UUID> findLiveCheckinIds(UUID userId);

    List<UUID> findReviewIds(UUID userId);

    /** Live trips the person owns that no other account has joined. */
    List<UUID> findSoloOwnedTripIds(UUID userId);

    /** Accounts marked deleted whose row still holds personal data, oldest first. */
    List<UUID> findUnerasedDeletedUserIds(String anonymousEmailSuffix, int limit);

    /** Follows, blocks, saved marks and posts, feed signals, map-search counters, notifications. */
    void deletePrivateRecords(UUID userId);

    /** Pengoo's profile and every 1:1 thread with its turns, proposals, notes and messages. */
    void deleteAssistantData(UUID userId);

    /** Prompts and answers logged for the person's AI calls; the cost rows stay. */
    void clearAiCallPayloads(UUID userId);

    /** Comments on posts, check-ins and trip activities. */
    void removeComments(UUID userId);

    /** Cancels the meetups the person hosts and takes them out of the ones they joined. */
    void withdrawFromMeetups(UUID userId);

    /** Replaces every personal column of the users row and marks it deleted; true when it existed. */
    boolean anonymiseUser(UUID userId, String email, String fullName);
}

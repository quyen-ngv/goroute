package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * SQL for erasing a deleted account (AccountErasureService). Every statement is keyed by the
 * account's own id; none of them encodes a rule beyond "everything of this person".
 */
@Mapper
public interface AccountErasureMapper {

    List<UUID> findLiveCheckinIds(@Param("userId") UUID userId);

    List<UUID> findReviewIds(@Param("userId") UUID userId);

    /** Live trips the person owns that no other account has joined. */
    List<UUID> findSoloOwnedTripIds(@Param("userId") UUID userId);

    /** Accounts soft-deleted before erasure existed (or by an older path), oldest first. */
    List<UUID> findUnerasedDeletedUserIds(@Param("emailSuffix") String emailSuffix, @Param("limit") int limit);

    int deleteFollows(@Param("userId") UUID userId);

    int deleteBlocks(@Param("userId") UUID userId);

    int deleteSavedPlaceMarks(@Param("userId") UUID userId);

    int deleteSavedPosts(@Param("userId") UUID userId);

    int deleteFeedImpressions(@Param("userId") UUID userId);

    int deleteFeedFeedback(@Param("userId") UUID userId);

    int deleteAuthorAffinity(@Param("userId") UUID userId);

    int deleteMapSearchUsage(@Param("userId") UUID userId);

    int deleteNotifications(@Param("userId") UUID userId);

    int deleteScheduledDeliveries(@Param("userId") UUID userId);

    int deleteAssistantProfile(@Param("userId") UUID userId);

    int deleteAssistantMemories(@Param("userId") UUID userId);

    int deleteAssistantProposals(@Param("userId") UUID userId);

    int deleteAssistantTurns(@Param("userId") UUID userId);

    /** The person's 1:1 Pengoo threads; members and messages go with them (ON DELETE CASCADE). */
    int deleteAssistantThreads(@Param("userId") UUID userId);

    int clearAiCallPayloads(@Param("userId") UUID userId);

    int softDeleteContentComments(@Param("userId") UUID userId);

    int softDeleteActivityComments(@Param("userId") UUID userId);

    int cancelHostedMeetups(@Param("userId") UUID userId);

    int leaveMeetups(@Param("userId") UUID userId);

    /** Replaces every personal column of the row and marks it deleted. */
    int anonymiseUser(@Param("userId") UUID userId,
                      @Param("email") String email,
                      @Param("fullName") String fullName);
}

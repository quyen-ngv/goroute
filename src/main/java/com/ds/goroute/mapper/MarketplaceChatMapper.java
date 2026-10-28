package com.ds.goroute.mapper;
import com.ds.goroute.entity.*;import org.apache.ibatis.annotations.*;import java.time.LocalDateTime;import java.util.*;
@Mapper
public interface MarketplaceChatMapper {
 int insertConversation(MarketplaceConversation v);int insertMember(@Param("conversationId")UUID c,@Param("userId")UUID u,@Param("role")String r,@Param("joinedAt")LocalDateTime j);
 MarketplaceConversation findConversation(@Param("id")UUID id,@Param("viewerId")UUID viewerId);MarketplaceConversation findByHotelBooking(@Param("id")UUID id,@Param("viewerId")UUID viewerId);MarketplaceConversation findByActivityOrder(@Param("id")UUID id,@Param("viewerId")UUID viewerId);
 List<MarketplaceConversation> findForUser(@Param("viewerId")UUID userId,@Param("conversationTypes")List<String> conversationTypes,@Param("limit")int limit,@Param("offset")int offset);List<MarketplaceConversation> findForOrganization(@Param("organizationId")UUID org,@Param("viewerId")UUID viewerId,@Param("status")String status,@Param("limit")int limit,@Param("offset")int offset);List<MarketplaceConversation> findAdmin(@Param("query")String query,@Param("status")List<String> status,@Param("conversationType")List<String> conversationType,@Param("sort")String sort,@Param("descending")boolean descending,@Param("limit")int limit,@Param("offset")int offset);
 List<MarketplaceConversation> findForOrganizationAccessible(@Param("organizationId")UUID org,@Param("viewerId")UUID viewerId,@Param("status")String status,@Param("hotelIds")List<UUID> hotelIds,@Param("activityBookingIds")List<UUID> activityBookingIds,@Param("limit")int limit,@Param("offset")int offset);
 List<UUID> findOrganizationChatHotelIds(@Param("organizationId")UUID org);List<UUID> findOrganizationChatActivityBookingIds(@Param("organizationId")UUID org);
 boolean canAccess(@Param("conversationId")UUID c,@Param("userId")UUID u);UUID lockConversation(@Param("id")UUID id);long nextSequence(@Param("id")UUID id);
 int insertMessage(MarketplaceMessage v);MarketplaceMessage findMessageByClientId(@Param("conversationId")UUID c,@Param("senderId")UUID s,@Param("clientId")String clientId);MarketplaceMessage findMessage(@Param("id")UUID id);
 List<MarketplaceMessage> findMessages(@Param("conversationId")UUID c,@Param("afterSequence")Long after,@Param("limit")int limit);
 int updateConversationLastMessage(@Param("id")UUID id,@Param("at")LocalDateTime at);int markRead(@Param("conversationId")UUID c,@Param("userId")UUID u,@Param("messageId")UUID m);
 int updateConversation(@Param("id")UUID id,@Param("status")String status,@Param("assignedMemberId")UUID member,@Param("unassign")boolean unassign,@Param("at")LocalDateTime at);
 int softDeleteMessage(@Param("conversationId")UUID conversationId,@Param("messageId")UUID messageId,@Param("at")LocalDateTime at);
 long countUnreadForOrganization(@Param("organizationId")UUID organizationId,@Param("viewerId")UUID viewerId);
 long countOpenForOrganization(@Param("organizationId")UUID organizationId);
 /**
  * A direct conversation whose active members are exactly {@code userIds}, under the same
  * organization (or none). Used to reuse the existing thread instead of opening a new one
  * every time someone taps Message.
  */
 MarketplaceConversation findDirectByMembers(@Param("userIds")java.util.List<UUID> userIds,@Param("memberCount")int memberCount,@Param("organizationId")UUID organizationId);

 /** Members of a conversation with their display profile, joined order. */
 java.util.List<MarketplaceConversationParticipant> findParticipants(@Param("conversationIds")java.util.List<UUID> conversationIds);

 /** The first {@code perConversation} members of each conversation, joined order. */
 List<MarketplaceConversationParticipant> findParticipantPreviews(@Param("conversationIds") List<UUID> conversationIds,
                                                                  @Param("perConversation") int perConversation);

 /** The group chat of one trip, if it has been created. */
 MarketplaceConversation findByTrip(@Param("tripId")UUID tripId,@Param("viewerId")UUID viewerId);
 MarketplaceConversation findByAssistantUser(@Param("userId")UUID userId,@Param("viewerId")UUID viewerId);

 /** The group chat of one meetup, if it has been created. */
 MarketplaceConversation findByMeetup(@Param("meetupId")UUID meetupId,@Param("viewerId")UUID viewerId);

 /** Whether the person hosts the meetup or holds a JOINED seat in it right now. */
 boolean isMeetupHostOrJoined(@Param("meetupId")UUID meetupId,@Param("userId")UUID userId);

 /** The group chat of one local event, if it has been created. */
 MarketplaceConversation findByLocalEvent(@Param("eventId")UUID eventId,@Param("viewerId")UUID viewerId);

 /** Whether the person is going to the event right now. */
 boolean isEventAttendee(@Param("eventId")UUID eventId,@Param("userId")UUID userId);

 /** Ids of everyone currently in the thread, for reconciling against another member list. */
 List<UUID> findActiveMemberIds(@Param("conversationId")UUID conversationId);

 /** Every current member's read marker and unread count, in one query for the whole thread. */
 List<com.ds.goroute.entity.MarketplaceMemberInboxState> findMemberInboxStates(@Param("conversationId") UUID conversationId,
                                                                               @Param("userId") UUID userId);

 /** Every current member's read marker alone, without the unread counts. */
 List<com.ds.goroute.entity.MarketplaceMemberInboxState> findMemberReadSequences(@Param("conversationId") UUID conversationId);

 /** The same people, less the ones who muted the thread: who a new message is announced to. */
 List<UUID> findNotifiableMemberIds(@Param("conversationId")UUID conversationId);

 int markMemberLeft(@Param("conversationId")UUID conversationId,@Param("userId")UUID userId,@Param("at")LocalDateTime at);

 int updateMuted(@Param("conversationId")UUID conversationId,@Param("userId")UUID userId,@Param("mutedUntil")LocalDateTime mutedUntil);

 int updatePinnedMessage(@Param("conversationId")UUID conversationId,@Param("messageId")UUID messageId,@Param("at")LocalDateTime at);

 /** Threads with something unread in them, ignoring the muted ones: the tab badge. */
 long countUnreadConversationsForUser(@Param("viewerId")UUID viewerId);

 /** The newest page of a thread, returned oldest-first. */
 List<MarketplaceMessage> findLatestMessages(@Param("conversationId")UUID conversationId,@Param("beforeSequence")Long beforeSequence,@Param("limit")int limit);

 List<MarketplaceMessage> searchMessages(@Param("conversationId")UUID conversationId,@Param("query")String query,@Param("limit")int limit);

 int softDeleteOwnMessage(@Param("conversationId")UUID conversationId,@Param("messageId")UUID messageId,@Param("senderId")UUID senderId,@Param("at")LocalDateTime at);

 int updateMessageContent(@Param("conversationId")UUID conversationId,@Param("messageId")UUID messageId,@Param("senderId")UUID senderId,@Param("content")String content,@Param("at")LocalDateTime at);

 int insertReaction(@Param("messageId")UUID messageId,@Param("userId")UUID userId,@Param("emoji")String emoji,@Param("at")LocalDateTime at);

 int deleteReaction(@Param("messageId")UUID messageId,@Param("userId")UUID userId,@Param("emoji")String emoji);

 List<MarketplaceMessageReaction> findReactions(@Param("messageIds")List<UUID> messageIds);

 /** Takes the transaction-scoped advisory lock on one person's Pengoo threads. */
 boolean lockAssistantOwner(@Param("userId") UUID userId);

 /** The owner's live thread they wrote in most recently, if its last message is not before {@code since}. */
 UUID findRecentUsedAssistantThread(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

 /** The owner's newest live thread they have not written in yet. */
 UUID findNewestUnusedAssistantThread(@Param("userId") UUID userId);

 /** Whether the person ever had a Pengoo thread, archived ones included. */
 boolean hasAssistantThread(@Param("userId") UUID userId);

 /** The owner's live, used threads, latest first, optionally only those whose shown title has {@code query}. */
 List<AssistantThreadRow> findAssistantThreads(@Param("viewerId") UUID ownerId, @Param("query") String query,
                                               @Param("limit") int limit, @Param("offset") int offset);

 long countAssistantThreads(@Param("viewerId") UUID ownerId, @Param("query") String query);

 int renameAssistantThread(@Param("id") UUID conversationId, @Param("ownerId") UUID ownerId,
                           @Param("title") String title, @Param("at") LocalDateTime at);

 int setAssistantTitleIfEmpty(@Param("id") UUID conversationId, @Param("title") String title,
                              @Param("at") LocalDateTime at);

 int archiveAssistantThread(@Param("id") UUID conversationId, @Param("ownerId") UUID ownerId,
                            @Param("at") LocalDateTime at);
}

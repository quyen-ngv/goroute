package com.ds.goroute.repository;
import com.ds.goroute.entity.*;import java.time.LocalDateTime;import java.util.*;
public interface MarketplaceChatRepository {int insertConversation(MarketplaceConversation v);int addMember(UUID c,UUID u,String r,LocalDateTime j);Optional<MarketplaceConversation> find(UUID id,UUID viewer);Optional<MarketplaceConversation> findByHotelBooking(UUID id,UUID viewer);Optional<MarketplaceConversation> findByActivityOrder(UUID id,UUID viewer);List<MarketplaceConversation> findForUser(UUID u,List<String> conversationTypes,int l,int o);List<MarketplaceConversation> findForOrganization(UUID org,UUID viewer,String s,int l,int o);List<MarketplaceConversation> findAdmin(String q,List<String> s,List<String> types,String sort,boolean descending,int l,int o);boolean canAccess(UUID c,UUID u);void lock(UUID id);long nextSequence(UUID id);int insertMessage(MarketplaceMessage v);Optional<MarketplaceMessage> findMessageByClientId(UUID c,UUID s,String client);Optional<MarketplaceMessage> findMessage(UUID id);List<MarketplaceMessage> findMessages(UUID c,Long after,int l);int updateLast(UUID id,LocalDateTime at);int markRead(UUID c,UUID u,UUID m);int updateConversation(UUID id,String s,UUID a,boolean unassign,LocalDateTime at);int softDeleteMessage(UUID conversationId,UUID messageId,LocalDateTime at);long countUnreadForOrganization(UUID organizationId,UUID viewerId);long countOpenForOrganization(UUID organizationId);
 /** An existing direct thread between exactly these people, so Message reopens rather than duplicates. */
 Optional<MarketplaceConversation> findDirectByMembers(List<UUID> userIds,UUID organizationId);
 /** Members of the given conversations, with the profile a client needs to title a thread. */
 List<MarketplaceConversationParticipant> findParticipants(List<UUID> conversationIds);

 /** At most {@code perConversation} members of each conversation, for inbox rows. */
 List<MarketplaceConversationParticipant> findParticipantPreviews(List<UUID> conversationIds, int perConversation);

 /** The group chat of one trip, if it has been created. */
 Optional<MarketplaceConversation> findByTrip(UUID tripId,UUID viewer);
 /** A person's 1:1 conversation with the assistant. */
 Optional<MarketplaceConversation> findByAssistantUser(UUID userId,UUID viewer);

 /** The group chat of one meetup, if it has been created. */
 Optional<MarketplaceConversation> findByMeetup(UUID meetupId,UUID viewer);

 /** Live meetup-group access: the host, or a participant whose row is JOINED. */
 boolean isMeetupHostOrJoined(UUID meetupId,UUID userId);

 /** The group chat of one local event, if it has been created. */
 Optional<MarketplaceConversation> findByLocalEvent(UUID eventId,UUID viewer);

 /** Live event-group access: the person is going to the event. */
 boolean isEventAttendee(UUID eventId,UUID userId);

 /** Everyone currently in the thread, for reconciling against the trip's member list. */
 List<UUID> findActiveMemberIds(UUID conversationId);

 /** Each current member's read marker and unread count, for per-person inbox updates. */
 List<MarketplaceMemberInboxState> findMemberInboxStates(UUID conversationId);

 /** One member's read marker and unread count; empty when they are not a current member. */
 List<MarketplaceMemberInboxState> findMemberInboxState(UUID conversationId, UUID userId);

 /** Each current member's read marker, without unread counts. */
 List<MarketplaceMemberInboxState> findMemberReadSequences(UUID conversationId);

 /** The same people, less the ones who muted it: who a new message is announced to. */
 List<UUID> findNotifiableMemberIds(UUID conversationId);

 int markMemberLeft(UUID conversationId,UUID userId,LocalDateTime at);

 int updateMuted(UUID conversationId,UUID userId,LocalDateTime mutedUntil);

 int updatePinnedMessage(UUID conversationId,UUID messageId,LocalDateTime at);

 /** Threads with something unread in them, muted ones excluded: the tab badge. */
 long countUnreadConversationsForUser(UUID viewerId);

 /** The newest page of a thread, oldest-first. */
 List<MarketplaceMessage> findLatestMessages(UUID conversationId,Long beforeSequence,int limit);

 List<MarketplaceMessage> searchMessages(UUID conversationId,String query,int limit);

 int softDeleteOwnMessage(UUID conversationId,UUID messageId,UUID senderId,LocalDateTime at);

 int updateMessageContent(UUID conversationId,UUID messageId,UUID senderId,String content,LocalDateTime at);

 int insertReaction(UUID messageId,UUID userId,String emoji,LocalDateTime at);

 int deleteReaction(UUID messageId,UUID userId,String emoji);

 List<MarketplaceMessageReaction> findReactions(List<UUID> messageIds);

 /**
  * Serialises creating Pengoo threads for one person until the transaction ends (V261 dropped the
  * one-thread-per-person index). Must run inside a transaction.
  */
 void lockAssistantOwner(UUID userId);

 /** The owner's live thread they last wrote in, when its last message is at or after {@code since}. */
 Optional<UUID> findRecentUsedAssistantThread(UUID userId, LocalDateTime since);

 /** The owner's newest live thread with no message of theirs yet. */
 Optional<UUID> findNewestUnusedAssistantThread(UUID userId);

 /** Whether the person ever had a Pengoo thread, archived ones included. */
 boolean hasAssistantThread(UUID userId);

 /** A page of the owner's live, used threads, latest first; {@code query} matches the shown title. */
 List<AssistantThreadRow> findAssistantThreads(UUID ownerId, String query, int limit, int offset);

 long countAssistantThreads(UUID ownerId, String query);

 /** Renames a live thread of the owner; 0 when it is not theirs, not a Pengoo thread, or archived. */
 int renameAssistantThread(UUID conversationId, UUID ownerId, String title, LocalDateTime at);

 /** Sets a Pengoo thread's title unless it already has one. */
 int setAssistantTitleIfEmpty(UUID conversationId, String title, LocalDateTime at);

 /** Archives a thread of the owner (keeping an earlier archive time); 0 when it is not theirs. */
 int archiveAssistantThread(UUID conversationId, UUID ownerId, LocalDateTime at);}

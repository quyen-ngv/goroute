package com.ds.goroute.repository;
import com.ds.goroute.entity.*;import java.time.LocalDateTime;import java.util.*;
public interface MarketplaceChatRepository {int insertConversation(MarketplaceConversation v);int addMember(UUID c,UUID u,String r,LocalDateTime j);Optional<MarketplaceConversation> find(UUID id,UUID viewer);Optional<MarketplaceConversation> findByHotelBooking(UUID id,UUID viewer);Optional<MarketplaceConversation> findByActivityOrder(UUID id,UUID viewer);List<MarketplaceConversation> findForUser(UUID u,List<String> conversationTypes,int l,int o);List<MarketplaceConversation> findForOrganization(UUID org,UUID viewer,String s,int l,int o);List<MarketplaceConversation> findAdmin(String q,List<String> s,List<String> types,String sort,boolean descending,int l,int o);boolean canAccess(UUID c,UUID u);void lock(UUID id);long nextSequence(UUID id);int insertMessage(MarketplaceMessage v);Optional<MarketplaceMessage> findMessageByClientId(UUID c,UUID s,String client);Optional<MarketplaceMessage> findMessage(UUID id);List<MarketplaceMessage> findMessages(UUID c,Long after,int l);int updateLast(UUID id,LocalDateTime at);int markRead(UUID c,UUID u,UUID m);int updateConversation(UUID id,String s,UUID a,boolean unassign,LocalDateTime at);int softDeleteMessage(UUID conversationId,UUID messageId,LocalDateTime at);long countUnreadForOrganization(UUID organizationId,UUID viewerId);long countOpenForOrganization(UUID organizationId);
 /** An existing direct thread between exactly these people, so Message reopens rather than duplicates. */
 Optional<MarketplaceConversation> findDirectByMembers(List<UUID> userIds,UUID organizationId);
 /** Members of the given conversations, with the profile a client needs to title a thread. */
 List<MarketplaceConversationParticipant> findParticipants(List<UUID> conversationIds);

 /** The group chat of one trip, if it has been created. */
 Optional<MarketplaceConversation> findByTrip(UUID tripId,UUID viewer);

 /** Everyone currently in the thread, for reconciling against the trip's member list. */
 List<UUID> findActiveMemberIds(UUID conversationId);

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

 List<MarketplaceMessageReaction> findReactions(List<UUID> messageIds);}

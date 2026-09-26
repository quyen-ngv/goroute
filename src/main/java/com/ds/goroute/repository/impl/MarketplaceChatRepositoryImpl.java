package com.ds.goroute.repository.impl;
import com.ds.goroute.entity.*;import com.ds.goroute.mapper.MarketplaceChatMapper;import com.ds.goroute.repository.MarketplaceChatRepository;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Repository;import java.time.LocalDateTime;import java.util.*;
@Repository @RequiredArgsConstructor public class MarketplaceChatRepositoryImpl implements MarketplaceChatRepository {private final MarketplaceChatMapper m;public int insertConversation(MarketplaceConversation v){return m.insertConversation(v);}public int addMember(UUID c,UUID u,String r,LocalDateTime j){return m.insertMember(c,u,r,j);}public Optional<MarketplaceConversation> find(UUID id,UUID v){return Optional.ofNullable(m.findConversation(id,v));}public Optional<MarketplaceConversation> findByHotelBooking(UUID id,UUID v){return Optional.ofNullable(m.findByHotelBooking(id,v));}public Optional<MarketplaceConversation> findByActivityOrder(UUID id,UUID v){return Optional.ofNullable(m.findByActivityOrder(id,v));}public List<MarketplaceConversation> findForUser(UUID u,List<String> types,int l,int o){return m.findForUser(u,types==null||types.isEmpty()?null:types,l,o);}public List<MarketplaceConversation> findForOrganization(UUID g,UUID v,String s,int l,int o){return m.findForOrganization(g,v,s,l,o);}public List<MarketplaceConversation> findAdmin(String q,List<String> s,List<String> types,String sort,boolean desc,int l,int o){return m.findAdmin(q,s,types,sort,desc,l,o);}public boolean canAccess(UUID c,UUID u){return m.canAccess(c,u);}public void lock(UUID id){m.lockConversation(id);}public long nextSequence(UUID id){return m.nextSequence(id);}public int insertMessage(MarketplaceMessage v){return m.insertMessage(v);}public Optional<MarketplaceMessage> findMessageByClientId(UUID c,UUID s,String i){return Optional.ofNullable(m.findMessageByClientId(c,s,i));}public Optional<MarketplaceMessage> findMessage(UUID id){return Optional.ofNullable(m.findMessage(id));}public List<MarketplaceMessage> findMessages(UUID c,Long a,int l){return m.findMessages(c,a,l);}public int updateLast(UUID id,LocalDateTime a){return m.updateConversationLastMessage(id,a);}public int markRead(UUID c,UUID u,UUID m1){return m.markRead(c,u,m1);}public int updateConversation(UUID id,String s,UUID a,boolean unassign,LocalDateTime at){return m.updateConversation(id,s,a,unassign,at);}public int softDeleteMessage(UUID c,UUID id,LocalDateTime at){return m.softDeleteMessage(c,id,at);}public long countUnreadForOrganization(UUID o,UUID v){return m.countUnreadForOrganization(o,v);}public long countOpenForOrganization(UUID o){return m.countOpenForOrganization(o);}

    @Override
    public Optional<MarketplaceConversation> findDirectByMembers(List<UUID> userIds,UUID organizationId){
        if(userIds==null||userIds.isEmpty())return Optional.empty();
        List<UUID> distinct=userIds.stream().distinct().toList();
        return Optional.ofNullable(m.findDirectByMembers(distinct,distinct.size(),organizationId));
    }

    @Override
    public List<MarketplaceConversationParticipant> findParticipants(List<UUID> conversationIds){
        if(conversationIds==null||conversationIds.isEmpty())return List.of();
        return m.findParticipants(conversationIds.stream().distinct().toList());
    }

    @Override
    public Optional<MarketplaceConversation> findByTrip(UUID tripId,UUID viewer){
        return tripId==null?Optional.empty():Optional.ofNullable(m.findByTrip(tripId,viewer));
    }

    @Override
    public List<UUID> findActiveMemberIds(UUID conversationId){return m.findActiveMemberIds(conversationId);}

    @Override
    public List<UUID> findNotifiableMemberIds(UUID conversationId){return m.findNotifiableMemberIds(conversationId);}

    @Override
    public int markMemberLeft(UUID conversationId,UUID userId,LocalDateTime at){return m.markMemberLeft(conversationId,userId,at);}

    @Override
    public int updateMuted(UUID conversationId,UUID userId,LocalDateTime mutedUntil){return m.updateMuted(conversationId,userId,mutedUntil);}

    @Override
    public int updatePinnedMessage(UUID conversationId,UUID messageId,LocalDateTime at){return m.updatePinnedMessage(conversationId,messageId,at);}

    @Override
    public long countUnreadConversationsForUser(UUID viewerId){return m.countUnreadConversationsForUser(viewerId);}

    @Override
    public List<MarketplaceMessage> findLatestMessages(UUID conversationId,Long beforeSequence,int limit){
        return m.findLatestMessages(conversationId,beforeSequence,limit);
    }

    @Override
    public List<MarketplaceMessage> searchMessages(UUID conversationId,String query,int limit){
        return m.searchMessages(conversationId,query,limit);
    }

    @Override
    public int softDeleteOwnMessage(UUID conversationId,UUID messageId,UUID senderId,LocalDateTime at){
        return m.softDeleteOwnMessage(conversationId,messageId,senderId,at);
    }

    @Override
    public int updateMessageContent(UUID conversationId,UUID messageId,UUID senderId,String content,LocalDateTime at){
        return m.updateMessageContent(conversationId,messageId,senderId,content,at);
    }

    @Override
    public int insertReaction(UUID messageId,UUID userId,String emoji,LocalDateTime at){
        return m.insertReaction(messageId,userId,emoji,at);
    }

    @Override
    public int deleteReaction(UUID messageId,UUID userId,String emoji){
        return m.deleteReaction(messageId,userId,emoji);
    }

    @Override
    public List<MarketplaceMessageReaction> findReactions(List<UUID> messageIds){
        if(messageIds==null||messageIds.isEmpty())return List.of();
        return m.findReactions(messageIds.stream().distinct().toList());
    }
}

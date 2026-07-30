package com.ds.goroute.mapper;
import com.ds.goroute.entity.*;import org.apache.ibatis.annotations.*;import java.time.LocalDateTime;import java.util.*;
@Mapper
public interface MarketplaceChatMapper {
 int insertConversation(MarketplaceConversation v);int insertMember(@Param("conversationId")UUID c,@Param("userId")UUID u,@Param("role")String r,@Param("joinedAt")LocalDateTime j);
 MarketplaceConversation findConversation(@Param("id")UUID id,@Param("viewerId")UUID viewerId);MarketplaceConversation findByHotelBooking(@Param("id")UUID id,@Param("viewerId")UUID viewerId);MarketplaceConversation findByActivityOrder(@Param("id")UUID id,@Param("viewerId")UUID viewerId);
 List<MarketplaceConversation> findForUser(@Param("userId")UUID userId,@Param("limit")int limit,@Param("offset")int offset);List<MarketplaceConversation> findForOrganization(@Param("organizationId")UUID org,@Param("viewerId")UUID viewerId,@Param("status")String status,@Param("limit")int limit,@Param("offset")int offset);List<MarketplaceConversation> findAdmin(@Param("query")String query,@Param("status")String status,@Param("limit")int limit,@Param("offset")int offset);
 boolean canAccess(@Param("conversationId")UUID c,@Param("userId")UUID u);UUID lockConversation(@Param("id")UUID id);long nextSequence(@Param("id")UUID id);
 int insertMessage(MarketplaceMessage v);MarketplaceMessage findMessageByClientId(@Param("conversationId")UUID c,@Param("senderId")UUID s,@Param("clientId")String clientId);MarketplaceMessage findMessage(@Param("id")UUID id);
 List<MarketplaceMessage> findMessages(@Param("conversationId")UUID c,@Param("afterSequence")Long after,@Param("limit")int limit);
 int updateConversationLastMessage(@Param("id")UUID id,@Param("at")LocalDateTime at);int markRead(@Param("conversationId")UUID c,@Param("userId")UUID u,@Param("messageId")UUID m);
 int updateConversation(@Param("id")UUID id,@Param("status")String status,@Param("assignedMemberId")UUID member,@Param("at")LocalDateTime at);
 int softDeleteMessage(@Param("conversationId")UUID conversationId,@Param("messageId")UUID messageId,@Param("at")LocalDateTime at);
}

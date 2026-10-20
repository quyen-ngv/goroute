package com.ds.goroute.mapper;

import com.ds.goroute.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface NotificationMapper {
    int insert(Notification notification);
    
    Notification selectById(@Param("id") UUID id);
    
    Notification findById(@Param("id") UUID id);
    
    List<Notification> selectByUserId(@Param("userId") UUID userId);
    
    List<Notification> selectByUserIdAndTripId(@Param("userId") UUID userId, @Param("tripId") UUID tripId);
    
    List<Notification> selectUnreadByUserId(@Param("userId") UUID userId);
    
    List<Notification> selectUnreadByUserIdAndTripId(@Param("userId") UUID userId, @Param("tripId") UUID tripId);
    
    List<Notification> findByUserId(@Param("userId") UUID userId,
                                     @Param("tripId") UUID tripId,
                                     @Param("unreadOnly") Boolean unreadOnly,
                                     @Param("limit") Integer limit, 
                                     @Param("offset") Integer offset);
    
    Integer countUnread(@Param("userId") UUID userId);

    Notification findRecentUnreadSocialNotification(
            @Param("userId") UUID userId,
            @Param("type") com.ds.goroute.type.NotificationType type,
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId);

    Notification findLatestConversationNotification(@Param("userId") UUID userId,
                                                    @Param("conversationId") UUID conversationId);

    int updateSocialNotification(Notification notification);
    
    int updateById(Notification notification);
    
    int markAsRead(@Param("id") UUID id, @Param("userId") UUID userId);
    
    int markAllAsRead(@Param("userId") UUID userId, @Param("tripId") UUID tripId);

    int markConversationNotificationsRead(@Param("userId") UUID userId,
                                          @Param("conversationId") UUID conversationId);

    /** Transaction-scoped advisory lock on one (recipient, type, target); see the repository. */
    Boolean lockTarget(@Param("userId") UUID userId,
                       @Param("type") com.ds.goroute.type.NotificationType type,
                       @Param("targetType") String targetType,
                       @Param("targetId") UUID targetId);
    
    int deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    List<UUID> insertFollowerNotifications(@Param("recipientIds") List<UUID> recipientIds,
                                           @Param("type") String type,
                                           @Param("data") String data,
                                           @Param("actorId") UUID actorId,
                                           @Param("dedupeKey") String dedupeKey);

    List<UUID> findOverFollowingDailyCap(@Param("userIds") List<UUID> userIds,
                                         @Param("hours") int hours,
                                         @Param("cap") int cap);

    List<Notification> findUnreadFollowingPosts(@Param("userId") UUID userId,
                                                @Param("hours") int hours,
                                                @Param("limit") int limit);

    int markFollowingPostsRead(@Param("userId") UUID userId,
                               @Param("dedupeKeys") java.util.Collection<String> dedupeKeys);
}

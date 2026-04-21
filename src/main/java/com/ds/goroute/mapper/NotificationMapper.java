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
                                     @Param("unreadOnly") Boolean unreadOnly,
                                     @Param("limit") Integer limit, 
                                     @Param("offset") Integer offset);
    
    Integer countUnread(@Param("userId") UUID userId);
    
    int updateById(Notification notification);
    
    int markAsRead(@Param("id") UUID id);
    
    int markAllAsRead(@Param("userId") UUID userId);
    
    int deleteById(@Param("id") UUID id);
}

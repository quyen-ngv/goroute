package com.ds.goroute.mapper;

import com.ds.goroute.dto.response.AdminNotificationHistoryResponse;
import com.ds.goroute.dto.response.AdminNotificationRecipientResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * Audience resolution for admin announcements. Every query shares one WHERE fragment so the
 * preview count, the recipient sample and the actual send can never disagree.
 */
@Mapper
public interface AdminNotificationMapper {

    long countAudience(@Param("audience") String audience,
                       @Param("userIds") List<UUID> userIds,
                       @Param("withinDays") Integer withinDays,
                       @Param("onlyWithDevice") boolean onlyWithDevice);

    List<AdminNotificationRecipientResponse> findAudienceRecipients(@Param("audience") String audience,
                                                                    @Param("userIds") List<UUID> userIds,
                                                                    @Param("withinDays") Integer withinDays,
                                                                    @Param("onlyWithDevice") boolean onlyWithDevice,
                                                                    @Param("limit") int limit,
                                                                    @Param("offset") int offset);

    List<UUID> findAudienceUserIds(@Param("audience") String audience,
                                   @Param("userIds") List<UUID> userIds,
                                   @Param("withinDays") Integer withinDays,
                                   @Param("onlyWithDevice") boolean onlyWithDevice,
                                   @Param("limit") int limit);

    /** Free-text picker over username, full name and email. */
    List<AdminNotificationRecipientResponse> searchRecipients(@Param("search") String search,
                                                              @Param("limit") int limit,
                                                              @Param("offset") int offset);

    long countSearchRecipients(@Param("search") String search);

    List<AdminNotificationHistoryResponse> findHistory(@Param("search") String search,
                                                      @Param("sort") String sort,
                                                      @Param("descending") boolean descending,
                                                       @Param("limit") int limit,
                                                       @Param("offset") int offset);

    long countHistory(@Param("search") String search);
}

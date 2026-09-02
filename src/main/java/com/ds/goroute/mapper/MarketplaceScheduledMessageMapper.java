package com.ds.goroute.mapper;

import com.ds.goroute.entity.MarketplaceScheduledMessage;
import com.ds.goroute.entity.MarketplaceScheduledMessageRun;
import com.ds.goroute.entity.ScheduledMessageTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface MarketplaceScheduledMessageMapper {
    int insert(MarketplaceScheduledMessage rule);
    int update(MarketplaceScheduledMessage rule);
    int delete(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
    MarketplaceScheduledMessage findById(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
    List<MarketplaceScheduledMessage> findByOrganization(@Param("organizationId") UUID organizationId, @Param("limit") int limit);
    int countByOrganization(@Param("organizationId") UUID organizationId);
    /** Every rule the job must consider on this tick, oldest first so the order is deterministic. */
    List<MarketplaceScheduledMessage> findEnabled(@Param("limit") int limit);

    /**
     * Hotel bookings whose trigger moment has passed within the look-back window and that have no
     * run row for this rule yet.
     *
     * @param now         the one clock, supplied by Java (server-local wall time)
     * @param serverZone  the JVM zone {@code now} is expressed in, so property-local triggers can be compared
     */
    List<ScheduledMessageTarget> findDueHotelTargets(@Param("scheduledMessageId") UUID scheduledMessageId,
                                                     @Param("organizationId") UUID organizationId,
                                                     @Param("trigger") String trigger,
                                                     @Param("offsetHours") int offsetHours,
                                                     @Param("now") LocalDateTime now,
                                                     @Param("serverZone") String serverZone,
                                                     @Param("lookbackHours") int lookbackHours,
                                                     @Param("limit") int limit);

    List<ScheduledMessageTarget> findDueActivityTargets(@Param("scheduledMessageId") UUID scheduledMessageId,
                                                        @Param("organizationId") UUID organizationId,
                                                        @Param("trigger") String trigger,
                                                        @Param("offsetHours") int offsetHours,
                                                        @Param("now") LocalDateTime now,
                                                        @Param("serverZone") String serverZone,
                                                        @Param("lookbackHours") int lookbackHours,
                                                        @Param("limit") int limit);

    /** Current status of the booking, re-read inside the delivery transaction. */
    String findHotelBookingStatus(@Param("id") UUID id);
    String findActivityOrderStatus(@Param("id") UUID id);

    int insertRun(MarketplaceScheduledMessageRun run);
    int updateRunOutcome(@Param("id") UUID id, @Param("status") String status, @Param("detail") String detail,
                         @Param("conversationId") UUID conversationId, @Param("messageId") UUID messageId);
    List<MarketplaceScheduledMessageRun> findRuns(@Param("scheduledMessageId") UUID scheduledMessageId,
                                                  @Param("limit") int limit, @Param("offset") int offset);
}

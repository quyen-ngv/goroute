package com.ds.goroute.repository;

import com.ds.goroute.entity.MarketplaceScheduledMessage;
import com.ds.goroute.entity.MarketplaceScheduledMessageRun;
import com.ds.goroute.entity.ScheduledMessageTarget;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceScheduledMessageRepository {
    int insert(MarketplaceScheduledMessage rule);
    int update(MarketplaceScheduledMessage rule);
    int delete(UUID id, UUID organizationId);
    Optional<MarketplaceScheduledMessage> findById(UUID id, UUID organizationId);
    List<MarketplaceScheduledMessage> findByOrganization(UUID organizationId, int limit);
    int countByOrganization(UUID organizationId);
    List<MarketplaceScheduledMessage> findEnabled(int limit);

    List<ScheduledMessageTarget> findDueHotelTargets(UUID scheduledMessageId, UUID organizationId, String trigger,
                                                     int offsetHours, LocalDateTime now, String serverZone,
                                                     int lookbackHours, int limit);
    List<ScheduledMessageTarget> findDueActivityTargets(UUID scheduledMessageId, UUID organizationId, String trigger,
                                                        int offsetHours, LocalDateTime now, String serverZone,
                                                        int lookbackHours, int limit);

    Optional<String> findHotelBookingStatus(UUID id);
    Optional<String> findActivityOrderStatus(UUID id);

    int insertRun(MarketplaceScheduledMessageRun run);
    int updateRunOutcome(UUID id, String status, String detail, UUID conversationId, UUID messageId);
    List<MarketplaceScheduledMessageRun> findRuns(UUID scheduledMessageId, int limit, int offset);
}

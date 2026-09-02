package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.MarketplaceScheduledMessage;
import com.ds.goroute.entity.MarketplaceScheduledMessageRun;
import com.ds.goroute.entity.ScheduledMessageTarget;
import com.ds.goroute.mapper.MarketplaceScheduledMessageMapper;
import com.ds.goroute.repository.MarketplaceScheduledMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MarketplaceScheduledMessageRepositoryImpl implements MarketplaceScheduledMessageRepository {
    private final MarketplaceScheduledMessageMapper mapper;

    @Override public int insert(MarketplaceScheduledMessage rule) { return mapper.insert(rule); }
    @Override public int update(MarketplaceScheduledMessage rule) { return mapper.update(rule); }
    @Override public int delete(UUID id, UUID organizationId) { return mapper.delete(id, organizationId); }

    @Override public Optional<MarketplaceScheduledMessage> findById(UUID id, UUID organizationId) {
        return Optional.ofNullable(mapper.findById(id, organizationId));
    }

    @Override public List<MarketplaceScheduledMessage> findByOrganization(UUID organizationId, int limit) {
        return mapper.findByOrganization(organizationId, limit);
    }

    @Override public int countByOrganization(UUID organizationId) { return mapper.countByOrganization(organizationId); }
    @Override public List<MarketplaceScheduledMessage> findEnabled(int limit) { return mapper.findEnabled(limit); }

    @Override public List<ScheduledMessageTarget> findDueHotelTargets(UUID scheduledMessageId, UUID organizationId,
            String trigger, int offsetHours, LocalDateTime now, String serverZone, int lookbackHours, int limit) {
        return mapper.findDueHotelTargets(scheduledMessageId, organizationId, trigger, offsetHours, now, serverZone,
                lookbackHours, limit);
    }

    @Override public List<ScheduledMessageTarget> findDueActivityTargets(UUID scheduledMessageId, UUID organizationId,
            String trigger, int offsetHours, LocalDateTime now, String serverZone, int lookbackHours, int limit) {
        return mapper.findDueActivityTargets(scheduledMessageId, organizationId, trigger, offsetHours, now, serverZone,
                lookbackHours, limit);
    }

    @Override public Optional<String> findHotelBookingStatus(UUID id) {
        return Optional.ofNullable(mapper.findHotelBookingStatus(id));
    }

    @Override public Optional<String> findActivityOrderStatus(UUID id) {
        return Optional.ofNullable(mapper.findActivityOrderStatus(id));
    }

    @Override public int insertRun(MarketplaceScheduledMessageRun run) { return mapper.insertRun(run); }

    @Override public int updateRunOutcome(UUID id, String status, String detail, UUID conversationId, UUID messageId) {
        return mapper.updateRunOutcome(id, status, detail, conversationId, messageId);
    }

    @Override public List<MarketplaceScheduledMessageRun> findRuns(UUID scheduledMessageId, int limit, int offset) {
        return mapper.findRuns(scheduledMessageId, limit, offset);
    }
}

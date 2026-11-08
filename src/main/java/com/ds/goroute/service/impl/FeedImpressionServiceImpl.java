package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.FeedEventRequest;
import com.ds.goroute.dto.request.FeedImpressionRequest;
import com.ds.goroute.feed.event.FeedEventLogger;
import com.ds.goroute.feed.model.FeedEventRow;
import com.ds.goroute.mapper.FeedImpressionMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.FeedImpressionService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.FeedItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedImpressionServiceImpl implements FeedImpressionService {

    /** Rows deleted per statement by the retention job, so no single delete runs long. */
    static final int PURGE_SLICE = 5_000;
    /** Upper bound on slices per night; what is left goes the next night. */
    static final int PURGE_MAX_SLICES = 400;

    private final FeedImpressionMapper impressionMapper;
    private final BusinessConfigService config;
    private final FeedEventLogger eventLogger;

    @Override
    @Transactional
    public void record(UUID userId, List<FeedImpressionRequest.Item> items) {
        if (userId == null || items == null || items.isEmpty()) {
            return;
        }
        Map<String, FeedImpressionRequest.Item> merged = upsertImpressions(userId, items);
        List<FeedEventRow> events = new ArrayList<>(merged.size());
        for (FeedImpressionRequest.Item item : merged.values()) {
            events.add(event(userId, item.getType().name(), item.getId(), FeedEventRequest.IMPRESSION,
                    null, null, item.getDwellMs()));
            if (item.isOpened()) {
                events.add(event(userId, item.getType().name(), item.getId(), FeedEventRequest.OPEN,
                        null, null, null));
            }
        }
        eventLogger.log(events);
    }

    @Override
    @Transactional
    public void recordEvents(UUID userId, List<FeedEventRequest.Item> items) {
        if (userId == null || items == null || items.isEmpty()) {
            return;
        }
        List<FeedImpressionRequest.Item> sightings = new ArrayList<>();
        List<FeedEventRow> events = new ArrayList<>(items.size());
        for (FeedEventRequest.Item item : items) {
            if (item == null || item.getType() == null || item.getId() == null || item.getEvent() == null) {
                continue;
            }
            int dwellMs = item.getDwellMs() == null ? 0 : item.getDwellMs();
            if (FeedEventRequest.IMPRESSION.equals(item.getEvent())) {
                sightings.add(FeedImpressionRequest.Item.builder()
                        .type(item.getType()).id(item.getId()).dwellMs(dwellMs).build());
            } else if (FeedEventRequest.OPEN.equals(item.getEvent())) {
                sightings.add(FeedImpressionRequest.Item.builder()
                        .type(item.getType()).id(item.getId()).opened(true).build());
            }
            events.add(event(userId, item.getType().name(), item.getId(), item.getEvent(),
                    item.getSurface(), item.getPosition(), item.getDwellMs()));
        }
        if (!sightings.isEmpty()) {
            upsertImpressions(userId, sightings);
        }
        eventLogger.log(events);
    }

    /** The batch merged by (type, id) and upserted in one statement; returns the merged items. */
    private Map<String, FeedImpressionRequest.Item> upsertImpressions(UUID userId,
                                                                      List<FeedImpressionRequest.Item> items) {
        // ON CONFLICT DO UPDATE refuses to touch one row twice in a statement, so merge first.
        Map<String, FeedImpressionRequest.Item> merged = new LinkedHashMap<>();
        for (FeedImpressionRequest.Item item : items) {
            if (item == null || item.getType() == null || item.getId() == null) {
                continue;
            }
            merged.merge(item.getType().name() + ':' + item.getId(), copyOf(item), (first, second) -> {
                first.setDwellMs(Math.max(first.getDwellMs(), second.getDwellMs()));
                first.setOpened(first.isOpened() || second.isOpened());
                return first;
            });
        }
        if (!merged.isEmpty()) {
            impressionMapper.upsertBatch(userId, merged.values());
        }
        return merged;
    }

    private static FeedEventRow event(UUID userId, String itemType, UUID itemId, String event,
                                      String surface, Integer position, Integer dwellMs) {
        return FeedEventRow.builder()
                .userId(userId)
                .itemType(itemType)
                .itemId(itemId)
                .event(event)
                .surface(surface)
                .position(position)
                .dwellMs(dwellMs)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> seenIds(UUID userId, FeedItemType itemType, Collection<UUID> itemIds) {
        if (userId == null || itemType == null || itemIds == null || itemIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(impressionMapper.findSeenIds(userId, itemType, new HashSet<>(itemIds)));
    }

    @Override
    public int purgeExpired() {
        int days = config.getInt(BusinessConfigKey.FEED_IMPRESSION_RETENTION_DAYS);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int total = 0;
        for (int slice = 0; slice < PURGE_MAX_SLICES; slice++) {
            // Each slice commits on its own: no transaction here on purpose.
            int deleted = impressionMapper.deleteSeenBefore(cutoff, PURGE_SLICE);
            total += deleted;
            if (deleted < PURGE_SLICE) {
                break;
            }
        }
        return total;
    }

    private static FeedImpressionRequest.Item copyOf(FeedImpressionRequest.Item item) {
        return FeedImpressionRequest.Item.builder()
                .type(item.getType())
                .id(item.getId())
                .dwellMs(Math.max(0, item.getDwellMs()))
                .opened(item.isOpened())
                .build();
    }
}

package com.ds.goroute.repository;

import com.ds.goroute.entity.Settlement;
import com.ds.goroute.mapper.SettlementMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@code settlements} (V268): money handed from one person to another. */
@Repository
@RequiredArgsConstructor
public class SettlementRepository {

    private final SettlementMapper mapper;

    public void insert(Settlement settlement) {
        mapper.insert(settlement);
    }

    public void update(Settlement settlement) {
        mapper.updateById(settlement);
    }

    /** Deleted ones included; callers check {@code deletedAt}. */
    public Optional<Settlement> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean softDelete(UUID id) {
        return mapper.softDelete(id) == 1;
    }

    public boolean restore(UUID id) {
        return mapper.restore(id) == 1;
    }

    public List<Settlement> findActiveByTripIds(Collection<UUID> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) return List.of();
        List<Settlement> settlements = new ArrayList<>();
        for (List<UUID> chunk : chunks(tripIds)) settlements.addAll(mapper.selectActiveByTripIds(chunk));
        return settlements;
    }

    public List<Settlement> findTripPage(UUID tripId, boolean includeDeleted, int limit, int offset) {
        return mapper.selectTripPage(tripId, includeDeleted, limit, offset);
    }

    public long countTrip(UUID tripId, boolean includeDeleted) {
        return mapper.countTrip(tripId, includeDeleted);
    }

    public List<Settlement> findActiveBySplitIds(Collection<UUID> splitIds) {
        if (splitIds == null || splitIds.isEmpty()) return List.of();
        List<Settlement> settlements = new ArrayList<>();
        for (List<UUID> chunk : chunks(splitIds)) settlements.addAll(mapper.selectActiveBySplitIds(chunk));
        return settlements;
    }

    public void softDeleteBySplitIds(Collection<UUID> splitIds) {
        if (splitIds == null || splitIds.isEmpty()) return;
        for (List<UUID> chunk : chunks(splitIds)) mapper.softDeleteBySplitIds(chunk);
    }

    /** A guest became this user: payments the guest gave or got become the user's. */
    public void moveGuestToUser(UUID guestMemberId, UUID userId) {
        mapper.moveGuestFromToUser(guestMemberId, userId);
        mapper.moveGuestToToUser(guestMemberId, userId);
        mapper.softDeleteSelfSettlements(userId);
    }

    private static List<List<UUID>> chunks(Collection<UUID> ids) {
        return Lists.partition(ids.stream().filter(Objects::nonNull).distinct().toList(), 500);
    }
}

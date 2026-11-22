package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.PlaceEditChange;
import com.ds.goroute.entity.PlaceEditSubmitterStats;
import com.ds.goroute.entity.PlaceEditSuggestion;
import com.ds.goroute.mapper.PlaceEditSuggestionMapper;
import com.ds.goroute.repository.PlaceEditSuggestionRepository;
import com.ds.goroute.type.PlaceEditChangeStatus;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditRejectReason;
import com.ds.goroute.type.PlaceEditReviewChannel;
import com.ds.goroute.type.PlaceEditStatus;
import com.ds.goroute.type.PlaceEditType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaceEditSuggestionRepositoryImpl implements PlaceEditSuggestionRepository {

    private static final int ID_BATCH_SIZE = 500;

    private final PlaceEditSuggestionMapper mapper;

    @Override
    public void insertSuggestion(PlaceEditSuggestion suggestion) {
        mapper.insertSuggestion(suggestion);
    }

    @Override
    public Optional<PlaceEditSuggestion> findSuggestion(UUID id) {
        return Optional.ofNullable(mapper.findSuggestionById(id));
    }

    @Override
    public Optional<PlaceEditSuggestion> lockSuggestion(UUID id) {
        return Optional.ofNullable(mapper.lockSuggestion(id));
    }

    @Override
    public List<PlaceEditSuggestion> findByUser(UUID userId, int limit, int offset) {
        return mapper.findSuggestionsByUser(userId, limit, offset);
    }

    @Override
    public long countByUser(UUID userId) {
        return mapper.countSuggestionsByUser(userId);
    }

    @Override
    public long countSubmittedSince(UUID userId, LocalDateTime since) {
        return mapper.countSubmittedSince(userId, since);
    }

    @Override
    public long countPendingByUser(UUID userId) {
        return mapper.countPendingByUser(userId);
    }

    @Override
    public boolean cancel(UUID suggestionId, UUID userId, LocalDateTime now) {
        return mapper.cancelSuggestion(suggestionId, userId, now) == 1;
    }

    @Override
    public boolean recordReview(UUID suggestionId, PlaceEditStatus status, PlaceEditRejectReason rejectReason,
                                String reviewNote, PlaceEditReviewChannel channel, UUID reviewerId,
                                LocalDateTime reviewedAt) {
        return mapper.updateReview(suggestionId, status.name(), rejectReason == null ? null : rejectReason.name(),
                reviewNote, channel.name(), reviewerId, reviewedAt) == 1;
    }

    @Override
    public void insertChange(PlaceEditChange change) {
        mapper.insertChange(change);
    }

    @Override
    public Map<UUID, List<PlaceEditChange>> findChangesBySuggestionIds(Collection<UUID> suggestionIds) {
        Map<UUID, List<PlaceEditChange>> result = new LinkedHashMap<>();
        if (suggestionIds == null || suggestionIds.isEmpty()) {
            return result;
        }
        List<UUID> ids = suggestionIds.stream().filter(Objects::nonNull).distinct().toList();
        for (int from = 0; from < ids.size(); from += ID_BATCH_SIZE) {
            List<UUID> batch = ids.subList(from, Math.min(from + ID_BATCH_SIZE, ids.size()));
            for (PlaceEditChange change : mapper.findChangesBySuggestionIds(batch)) {
                result.computeIfAbsent(change.getSuggestionId(), ignored -> new ArrayList<>()).add(change);
            }
        }
        return result;
    }

    @Override
    public Optional<PlaceEditChange> findPendingUserChange(UUID userId, UUID placeId, PlaceEditField field) {
        return Optional.ofNullable(mapper.findPendingUserChange(userId, placeId, field.name()));
    }

    @Override
    public boolean moveChange(UUID changeId, UUID suggestionId, String oldValue, String newValue, LocalDateTime now) {
        return mapper.moveChange(changeId, suggestionId, oldValue, newValue, now) == 1;
    }

    @Override
    public boolean decideChange(UUID changeId, PlaceEditChangeStatus status, String appliedValue,
                                PlaceEditRejectReason rejectReason, LocalDateTime now) {
        return mapper.decideChange(changeId, status.name(), appliedValue,
                rejectReason == null ? null : rejectReason.name(), now) == 1;
    }

    @Override
    public void cancelChangesOfSuggestion(UUID suggestionId, LocalDateTime now) {
        mapper.cancelChangesOfSuggestion(suggestionId, now);
    }

    @Override
    public int supersedePending(UUID placeId, PlaceEditField field, UUID exceptSuggestionId, LocalDateTime now) {
        return mapper.supersedePending(placeId, field.name(), exceptSuggestionId, now);
    }

    @Override
    public List<PlaceEditChange> findPendingChangesOfUserForPlace(UUID userId, UUID placeId) {
        return mapper.findPendingChangesOfUserForPlace(userId, placeId);
    }

    @Override
    public List<PlaceEditSuggestion> findAdminPage(PlaceEditStatus status, PlaceEditType type, UUID placeId,
                                                   UUID userId, int limit, int offset) {
        return mapper.findAdminPage(status == null ? null : status.name(), type == null ? null : type.name(),
                placeId, userId, limit, offset);
    }

    @Override
    public long countAdmin(PlaceEditStatus status, PlaceEditType type, UUID placeId, UUID userId) {
        return mapper.countAdmin(status == null ? null : status.name(), type == null ? null : type.name(),
                placeId, userId);
    }

    @Override
    public List<PlaceEditSuggestion> findOtherPendingForPlace(UUID placeId, UUID exceptId) {
        return mapper.findOtherPendingForPlace(placeId, exceptId);
    }

    @Override
    public PlaceEditSubmitterStats findSubmitterStats(UUID userId) {
        PlaceEditSubmitterStats stats = mapper.findSubmitterStats(userId);
        return stats == null ? new PlaceEditSubmitterStats(0, 0) : stats;
    }

    @Override
    public long countRewardsSince(UUID userId, LocalDateTime since) {
        return mapper.countRewardsSince(userId, since);
    }
}

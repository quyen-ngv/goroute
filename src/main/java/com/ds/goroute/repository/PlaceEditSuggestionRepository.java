package com.ds.goroute.repository;

import com.ds.goroute.entity.PlaceEditChange;
import com.ds.goroute.entity.PlaceEditSubmitterStats;
import com.ds.goroute.entity.PlaceEditSuggestion;
import com.ds.goroute.type.PlaceEditChangeStatus;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditRejectReason;
import com.ds.goroute.type.PlaceEditReviewChannel;
import com.ds.goroute.type.PlaceEditStatus;
import com.ds.goroute.type.PlaceEditType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Place edit suggestions and their field changes (V286, epic 09). */
public interface PlaceEditSuggestionRepository {

    void insertSuggestion(PlaceEditSuggestion suggestion);

    Optional<PlaceEditSuggestion> findSuggestion(UUID id);

    /** The suggestion locked until the current transaction ends. */
    Optional<PlaceEditSuggestion> lockSuggestion(UUID id);

    List<PlaceEditSuggestion> findByUser(UUID userId, int limit, int offset);

    long countByUser(UUID userId);

    long countSubmittedSince(UUID userId, LocalDateTime since);

    long countPendingByUser(UUID userId);

    /** False when the suggestion is not the caller's or no longer pending. */
    boolean cancel(UUID suggestionId, UUID userId, LocalDateTime now);

    /** False when the suggestion was no longer pending. */
    boolean recordReview(UUID suggestionId, PlaceEditStatus status, PlaceEditRejectReason rejectReason,
                         String reviewNote, PlaceEditReviewChannel channel, UUID reviewerId, LocalDateTime reviewedAt);

    void insertChange(PlaceEditChange change);

    /** Changes grouped by suggestion id, in creation order; ids with none are absent. */
    Map<UUID, List<PlaceEditChange>> findChangesBySuggestionIds(Collection<UUID> suggestionIds);

    Optional<PlaceEditChange> findPendingUserChange(UUID userId, UUID placeId, PlaceEditField field);

    boolean moveChange(UUID changeId, UUID suggestionId, String oldValue, String newValue, LocalDateTime now);

    boolean decideChange(UUID changeId, PlaceEditChangeStatus status, String appliedValue,
                         PlaceEditRejectReason rejectReason, LocalDateTime now);

    void cancelChangesOfSuggestion(UUID suggestionId, LocalDateTime now);

    int supersedePending(UUID placeId, PlaceEditField field, UUID exceptSuggestionId, LocalDateTime now);

    List<PlaceEditChange> findPendingChangesOfUserForPlace(UUID userId, UUID placeId);

    List<PlaceEditSuggestion> findAdminPage(PlaceEditStatus status, PlaceEditType type, UUID placeId, UUID userId,
                                            int limit, int offset);

    long countAdmin(PlaceEditStatus status, PlaceEditType type, UUID placeId, UUID userId);

    List<PlaceEditSuggestion> findOtherPendingForPlace(UUID placeId, UUID exceptId);

    PlaceEditSubmitterStats findSubmitterStats(UUID userId);

    long countRewardsSince(UUID userId, LocalDateTime since);
}

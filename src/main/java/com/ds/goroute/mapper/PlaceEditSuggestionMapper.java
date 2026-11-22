package com.ds.goroute.mapper;

import com.ds.goroute.entity.PlaceEditChange;
import com.ds.goroute.entity.PlaceEditSubmitterStats;
import com.ds.goroute.entity.PlaceEditSuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** SQL of {@code place_edit_suggestions} and {@code place_edit_changes} (V286, epic 09). */
@Mapper
public interface PlaceEditSuggestionMapper {

    int insertSuggestion(PlaceEditSuggestion suggestion);

    PlaceEditSuggestion findSuggestionById(@Param("id") UUID id);

    /** The row locked for the review transaction. */
    PlaceEditSuggestion lockSuggestion(@Param("id") UUID id);

    List<PlaceEditSuggestion> findSuggestionsByUser(@Param("userId") UUID userId,
                                                    @Param("limit") int limit,
                                                    @Param("offset") int offset);

    long countSuggestionsByUser(@Param("userId") UUID userId);

    long countSubmittedSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    long countPendingByUser(@Param("userId") UUID userId);

    /** PENDING → CANCELLED for its own submitter only; 0 when not theirs or not pending. */
    int cancelSuggestion(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /** Writes the review outcome while the row is still PENDING; 0 otherwise. */
    int updateReview(@Param("id") UUID id,
                     @Param("status") String status,
                     @Param("rejectReason") String rejectReason,
                     @Param("reviewNote") String reviewNote,
                     @Param("reviewChannel") String reviewChannel,
                     @Param("reviewedBy") UUID reviewedBy,
                     @Param("reviewedAt") LocalDateTime reviewedAt);

    int insertChange(PlaceEditChange change);

    List<PlaceEditChange> findChangesBySuggestionIds(@Param("suggestionIds") List<UUID> suggestionIds);

    /** The submitter's own pending value for a place field (at most one, by index). */
    PlaceEditChange findPendingUserChange(@Param("userId") UUID userId,
                                          @Param("placeId") UUID placeId,
                                          @Param("field") String field);

    /** Re-attaches a pending change to a newer suggestion of the same person with a new value. */
    int moveChange(@Param("id") UUID id,
                   @Param("suggestionId") UUID suggestionId,
                   @Param("oldValue") String oldValue,
                   @Param("newValue") String newValue,
                   @Param("now") LocalDateTime now);

    int decideChange(@Param("id") UUID id,
                     @Param("status") String status,
                     @Param("appliedValue") String appliedValue,
                     @Param("rejectReason") String rejectReason,
                     @Param("now") LocalDateTime now);

    int cancelChangesOfSuggestion(@Param("suggestionId") UUID suggestionId, @Param("now") LocalDateTime now);

    /** Other suggestions' pending changes to the same place and field become SUPERSEDED. */
    int supersedePending(@Param("placeId") UUID placeId,
                         @Param("field") String field,
                         @Param("exceptSuggestionId") UUID exceptSuggestionId,
                         @Param("now") LocalDateTime now);

    List<PlaceEditChange> findPendingChangesOfUserForPlace(@Param("userId") UUID userId,
                                                           @Param("placeId") UUID placeId);

    List<PlaceEditSuggestion> findAdminPage(@Param("status") String status,
                                            @Param("type") String type,
                                            @Param("placeId") UUID placeId,
                                            @Param("userId") UUID userId,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    long countAdmin(@Param("status") String status,
                    @Param("type") String type,
                    @Param("placeId") UUID placeId,
                    @Param("userId") UUID userId);

    List<PlaceEditSuggestion> findOtherPendingForPlace(@Param("placeId") UUID placeId,
                                                       @Param("exceptId") UUID exceptId);

    PlaceEditSubmitterStats findSubmitterStats(@Param("userId") UUID userId);

    /** PLACE_EDIT Star grants the user received since the instant (star_transactions). */
    long countRewardsSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);
}

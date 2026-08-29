package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateCommentRequest;
import com.ds.goroute.dto.response.CommentResponse;

import java.util.List;
import java.util.UUID;

public interface CommentService {
    List<CommentResponse> getComments(UUID tripId, UUID activityId, UUID userId);
    CommentResponse createComment(UUID tripId, UUID activityId, CreateCommentRequest request, UUID userId);
    void deleteComment(UUID tripId, UUID activityId, UUID commentId, UUID userId);

    /**
     * Comment counts for every activity of a trip, so the itinerary can show a count on
     * each card from one request rather than one per card.
     */
    java.util.Map<UUID, Integer> getCommentCounts(UUID tripId, UUID userId);
}

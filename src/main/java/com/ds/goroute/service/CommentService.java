package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateCommentRequest;
import com.ds.goroute.dto.response.CommentResponse;

import java.util.List;
import java.util.UUID;

public interface CommentService {
    List<CommentResponse> getComments(UUID tripId, UUID activityId, UUID userId);
    CommentResponse createComment(UUID tripId, UUID activityId, CreateCommentRequest request, UUID userId);
    void deleteComment(UUID tripId, UUID activityId, UUID commentId, UUID userId);
}

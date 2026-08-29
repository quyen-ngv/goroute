package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateContentCommentRequest;
import com.ds.goroute.dto.request.UpdateContentCommentRequest;
import com.ds.goroute.dto.response.ContentCommentPageResponse;
import com.ds.goroute.dto.response.ContentCommentResponse;
import com.ds.goroute.type.ModeratedContentType;

import java.util.List;
import java.util.UUID;

public interface ContentCommentService {
    ContentCommentPageResponse getComments(ModeratedContentType contentType, UUID contentId, UUID parentId,
                                           String cursor, int limit, UUID viewerId);

    int getActiveCommentCount(ModeratedContentType contentType, UUID contentId, UUID viewerId);

    ContentCommentResponse createComment(CreateContentCommentRequest request, UUID userId);

    ContentCommentResponse updateComment(UUID commentId, UpdateContentCommentRequest request, UUID userId);

    ContentCommentResponse toggleLike(UUID commentId, UUID userId);

    void deleteComment(UUID commentId, UUID userId);
}

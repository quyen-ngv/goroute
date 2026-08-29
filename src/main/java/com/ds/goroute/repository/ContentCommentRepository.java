package com.ds.goroute.repository;

import com.ds.goroute.entity.ContentComment;
import com.ds.goroute.type.ModeratedContentType;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ContentCommentRepository {
    void insert(ContentComment comment);

    Optional<ContentComment> findById(UUID id);

    Optional<ContentComment> findByIdWithStats(UUID id, UUID viewerId);

    List<ContentComment> findByTarget(ModeratedContentType contentType, UUID contentId);

    List<ContentComment> findPage(ModeratedContentType contentType, UUID contentId, UUID parentId,
                                  LocalDateTime afterCreatedAt, UUID afterId, int limit, UUID viewerId);

    void softDelete(UUID id);

    void updateContent(UUID id, String content);

    boolean hasLike(UUID commentId, UUID userId);

    void insertLike(UUID commentId, UUID userId);

    void deleteLike(UUID commentId, UUID userId);

    int countActiveByTarget(ModeratedContentType contentType, UUID contentId);
}

package com.ds.goroute.repository;

import com.ds.goroute.entity.ContentComment;
import com.ds.goroute.type.ModeratedContentType;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
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

    /**
     * Active comment count per target, for a page of targets of one type, in one grouped
     * query per batch of ids. Every requested id is in the result; a target with no active
     * comment maps to 0. A target under an unrestored takedown maps to 0 too, except for
     * reviews, whose thread the per-target count still reads.
     */
    Map<UUID, Integer> countActiveByTargets(ModeratedContentType contentType, Collection<UUID> contentIds);
}

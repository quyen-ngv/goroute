package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ContentComment;
import com.ds.goroute.mapper.ContentCommentMapper;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.type.ModeratedContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ContentCommentRepositoryImpl implements ContentCommentRepository {
    private final ContentCommentMapper mapper;

    @Override
    public void insert(ContentComment comment) {
        mapper.insert(comment);
    }

    @Override
    public Optional<ContentComment> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public Optional<ContentComment> findByIdWithStats(UUID id, UUID viewerId) {
        return Optional.ofNullable(mapper.findByIdWithStats(id, viewerId));
    }

    @Override
    public List<ContentComment> findByTarget(ModeratedContentType contentType, UUID contentId) {
        return mapper.findByTarget(contentType.name(), contentId);
    }

    @Override
    public List<ContentComment> findPage(ModeratedContentType contentType, UUID contentId, UUID parentId,
                                         LocalDateTime afterCreatedAt, UUID afterId, int limit, UUID viewerId) {
        return mapper.findPage(contentType.name(), contentId, parentId, afterCreatedAt, afterId, limit, viewerId);
    }

    @Override
    public void softDelete(UUID id) {
        mapper.softDelete(id);
    }

    @Override
    public void updateContent(UUID id, String content) {
        mapper.updateContent(id, content);
    }

    @Override
    public boolean hasLike(UUID commentId, UUID userId) {
        return mapper.hasLike(commentId, userId);
    }

    @Override
    public void insertLike(UUID commentId, UUID userId) {
        mapper.insertLike(commentId, userId);
    }

    @Override
    public void deleteLike(UUID commentId, UUID userId) {
        mapper.deleteLike(commentId, userId);
    }

    @Override
    public int countActiveByTarget(ModeratedContentType contentType, UUID contentId) {
        return mapper.countActiveByTarget(contentType.name(), contentId);
    }
}

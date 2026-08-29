package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ContentReport;
import com.ds.goroute.entity.ContentTakedown;
import com.ds.goroute.entity.ModerationFlag;
import com.ds.goroute.mapper.ModerationFlagMapper;
import com.ds.goroute.repository.ModerationFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ModerationFlagRepositoryImpl implements ModerationFlagRepository {

    private final ModerationFlagMapper mapper;

    @Override
    public int upsert(ModerationFlag flag) {
        return mapper.upsert(flag);
    }

    @Override
    public Optional<ModerationFlag> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public Optional<ModerationFlag> findOpen(String contentType, UUID contentId, String source, String category) {
        return Optional.ofNullable(mapper.findOpen(contentType, contentId, source, category));
    }

    @Override
    public List<ModerationFlag> findQueue(String status, String contentType, String category,
                                          String source, int limit, int offset) {
        return mapper.findQueue(status, contentType, category, source, limit, offset);
    }

    @Override
    public long countQueue(String status, String contentType, String category, String source) {
        return mapper.countQueue(status, contentType, category, source);
    }

    @Override
    public int resolve(UUID id, String status, UUID reviewedBy, String resolutionNote) {
        return mapper.resolve(id, status, reviewedBy, resolutionNote);
    }

    @Override
    public int insertReport(ContentReport report) {
        return mapper.insertReport(report);
    }

    @Override
    public List<ContentReport> findReportsForContent(String contentType, UUID contentId) {
        return mapper.findReportsForContent(contentType, contentId);
    }

    @Override
    public int resolveReportsForFlag(UUID flagId, String status) {
        return mapper.resolveReportsForFlag(flagId, status);
    }

    @Override
    public int insertTakedown(ContentTakedown takedown) {
        return mapper.insertTakedown(takedown);
    }

    @Override
    public Optional<ContentTakedown> findActiveTakedown(String contentType, UUID contentId) {
        return Optional.ofNullable(mapper.findActiveTakedown(contentType, contentId));
    }

    @Override
    public List<UUID> findActiveTakedownIds(String contentType, List<UUID> contentIds) {
        return contentIds.isEmpty() ? List.of() : mapper.findActiveTakedownIds(contentType, contentIds);
    }

    @Override
    public int restoreTakedown(String contentType, UUID contentId, UUID restoredBy) {
        return mapper.restoreTakedown(contentType, contentId, restoredBy);
    }
}

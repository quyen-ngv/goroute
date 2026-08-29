package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ImageModerationResult;
import com.ds.goroute.entity.ModerationDecision;
import com.ds.goroute.mapper.ModerationAuditMapper;
import com.ds.goroute.repository.ModerationAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ModerationAuditRepositoryImpl implements ModerationAuditRepository {

    private final ModerationAuditMapper mapper;

    @Override
    public int insertDecision(ModerationDecision decision) {
        return mapper.insertDecision(decision);
    }

    @Override
    public int insertImageResult(ImageModerationResult result) {
        return mapper.insertImageResult(result);
    }

    @Override
    public List<Map<String, Object>> summarizeDecisions(LocalDateTime from, LocalDateTime to) {
        return mapper.summarizeDecisions(from, to);
    }

    @Override
    public Map<String, Object> summarizeQueue(LocalDateTime from, LocalDateTime to) {
        return mapper.summarizeQueue(from, to);
    }

    @Override
    public List<Map<String, Object>> rankFalsePositiveTerms(LocalDateTime from, int limit) {
        return mapper.rankFalsePositiveTerms(from, limit);
    }

    @Override
    public Map<String, Object> summarizeMisses(LocalDateTime from) {
        return mapper.summarizeMisses(from);
    }

    @Override
    public List<Map<String, Object>> rankImageCategories(LocalDateTime from) {
        return mapper.rankImageCategories(from);
    }
}

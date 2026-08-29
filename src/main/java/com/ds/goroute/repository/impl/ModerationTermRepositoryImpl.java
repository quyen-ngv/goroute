package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ModerationTerm;
import com.ds.goroute.mapper.ModerationTermMapper;
import com.ds.goroute.repository.ModerationTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ModerationTermRepositoryImpl implements ModerationTermRepository {

    private final ModerationTermMapper mapper;

    @Override
    public List<ModerationTerm> findAllActive() {
        return mapper.findAllActive();
    }

    @Override
    public List<ModerationTerm> findAdmin(String query, String category, Boolean isExemption,
                                          Boolean active, int limit, int offset) {
        return mapper.findAdmin(query, category, isExemption, active, limit, offset);
    }

    @Override
    public long countAdmin(String query, String category, Boolean isExemption, Boolean active) {
        return mapper.countAdmin(query, category, isExemption, active);
    }

    @Override
    public Optional<ModerationTerm> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public int insert(ModerationTerm term) {
        return mapper.insert(term);
    }

    @Override
    public int update(ModerationTerm term) {
        return mapper.update(term);
    }

    @Override
    public int delete(UUID id) {
        return mapper.delete(id);
    }

    @Override
    public int insertAudit(UUID termId, String action, String beforeValue, String afterValue, UUID changedBy) {
        return mapper.insertAudit(termId, action, beforeValue, afterValue, changedBy);
    }

    @Override
    public List<Map<String, Object>> findAudit(UUID termId, int limit) {
        return mapper.findAudit(termId, limit);
    }
}

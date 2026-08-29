package com.ds.goroute.repository;

import com.ds.goroute.entity.ModerationTerm;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence for the administrable term list and its change log (MOD-02). */
public interface ModerationTermRepository {

    List<ModerationTerm> findAllActive();

    List<ModerationTerm> findAdmin(String query, String category, Boolean isExemption,
                                   Boolean active, int limit, int offset);

    long countAdmin(String query, String category, Boolean isExemption, Boolean active);

    Optional<ModerationTerm> findById(UUID id);

    int insert(ModerationTerm term);

    int update(ModerationTerm term);

    int delete(UUID id);

    int insertAudit(UUID termId, String action, String beforeValue, String afterValue, UUID changedBy);

    List<Map<String, Object>> findAudit(UUID termId, int limit);
}

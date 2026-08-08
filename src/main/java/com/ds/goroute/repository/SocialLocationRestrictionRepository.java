package com.ds.goroute.repository;

import com.ds.goroute.entity.SocialLocationSubmissionEvent;
import com.ds.goroute.entity.SocialLocationUserRestriction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialLocationRestrictionRepository {
    Optional<SocialLocationUserRestriction> findByUserId(UUID userId);
    void save(SocialLocationUserRestriction restriction);
    void insertEvent(SocialLocationSubmissionEvent event);
    List<SocialLocationUserRestriction> findAdmin(String status, int limit, int offset);
    List<SocialLocationSubmissionEvent> findEventsByUserId(UUID userId, int limit);
    void reset(UUID userId);
}

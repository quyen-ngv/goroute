package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.SocialLocationSubmissionEvent;
import com.ds.goroute.entity.SocialLocationUserRestriction;
import com.ds.goroute.mapper.SocialLocationRestrictionMapper;
import com.ds.goroute.repository.SocialLocationRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SocialLocationRestrictionRepositoryImpl implements SocialLocationRestrictionRepository {
    private final SocialLocationRestrictionMapper mapper;

    @Override public Optional<SocialLocationUserRestriction> findByUserId(UUID userId) {
        return Optional.ofNullable(mapper.findByUserId(userId));
    }
    @Override public void save(SocialLocationUserRestriction restriction) { mapper.upsert(restriction); }
    @Override public void insertEvent(SocialLocationSubmissionEvent event) { mapper.insertEvent(event); }
    @Override public List<SocialLocationUserRestriction> findAdmin(String status, int limit, int offset) {
        return mapper.findAdmin(status, limit, offset);
    }
    @Override public List<SocialLocationSubmissionEvent> findEventsByUserId(UUID userId, int limit) {
        return mapper.findEventsByUserId(userId, limit);
    }
    @Override public void reset(UUID userId) { mapper.reset(userId); }
}

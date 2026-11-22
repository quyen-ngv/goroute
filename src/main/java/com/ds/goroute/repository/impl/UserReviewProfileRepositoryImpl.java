package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.UserReviewProfile;
import com.ds.goroute.mapper.UserReviewProfileMapper;
import com.ds.goroute.repository.UserReviewProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserReviewProfileRepositoryImpl implements UserReviewProfileRepository {
    private final UserReviewProfileMapper mapper;
    
    @Override
    public void save(UserReviewProfile profile) {
        mapper.insert(profile);
    }
    
    @Override
    public void update(UserReviewProfile profile) {
        mapper.update(profile);
    }
    
    @Override
    public Optional<UserReviewProfile> findByUserId(UUID userId) {
        return Optional.ofNullable(mapper.findByUserId(userId));
    }
    
    @Override
    public List<UserReviewProfile> findByUserIds(Collection<UUID> userIds) {
        // An empty IN () is a syntax error, and the answer is knowable here.
        return userIds == null || userIds.isEmpty() ? List.of() : mapper.findByUserIds(userIds);
    }

    @Override
    public void incrementReviewCount(UUID userId) {
        mapper.incrementReviewCount(userId);
    }
    
    @Override
    public void decrementReviewCount(UUID userId) {
        mapper.decrementReviewCount(userId);
    }
}

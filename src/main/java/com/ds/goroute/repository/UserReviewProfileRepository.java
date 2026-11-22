package com.ds.goroute.repository;

import com.ds.goroute.entity.UserReviewProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserReviewProfileRepository {
    void save(UserReviewProfile profile);
    void update(UserReviewProfile profile);
    Optional<UserReviewProfile> findByUserId(UUID userId);
    /** The profiles of several authors in one query; authors without a profile are absent. */
    List<UserReviewProfile> findByUserIds(Collection<UUID> userIds);
    void incrementReviewCount(UUID userId);
    void decrementReviewCount(UUID userId);
}

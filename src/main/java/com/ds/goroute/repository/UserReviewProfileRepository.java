package com.ds.goroute.repository;

import com.ds.goroute.entity.UserReviewProfile;
import java.util.Optional;
import java.util.UUID;

public interface UserReviewProfileRepository {
    void save(UserReviewProfile profile);
    void update(UserReviewProfile profile);
    Optional<UserReviewProfile> findByUserId(UUID userId);
    void incrementReviewCount(UUID userId);
    void decrementReviewCount(UUID userId);
}

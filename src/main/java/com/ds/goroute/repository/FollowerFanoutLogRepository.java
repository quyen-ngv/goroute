package com.ds.goroute.repository;

import com.ds.goroute.entity.FollowerFanoutLog;
import com.ds.goroute.type.FollowerFanoutStatus;

import java.util.UUID;

public interface FollowerFanoutLogRepository {

    void insert(FollowerFanoutLog log);

    void updateProgress(FollowerFanoutLog log);

    void finish(UUID id, FollowerFanoutStatus status, String error);

    /** Whether a fan-out of this author that was allowed to push started less than {@code minutes} ago. */
    boolean isPushCoolingDown(UUID authorId, int minutes);

    /** The author's posts held back by the cooldown since their last push, at most a day back. */
    int countHeldBackSinceLastPush(UUID authorId);
}

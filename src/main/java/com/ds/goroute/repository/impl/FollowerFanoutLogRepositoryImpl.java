package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.FollowerFanoutLog;
import com.ds.goroute.mapper.FollowerFanoutLogMapper;
import com.ds.goroute.repository.FollowerFanoutLogRepository;
import com.ds.goroute.type.FollowerFanoutStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FollowerFanoutLogRepositoryImpl implements FollowerFanoutLogRepository {

    /** The column is VARCHAR(1000). */
    private static final int MAX_ERROR_LENGTH = 1000;

    private final FollowerFanoutLogMapper mapper;

    @Override
    public void insert(FollowerFanoutLog log) {
        mapper.insert(log);
    }

    @Override
    public void updateProgress(FollowerFanoutLog log) {
        mapper.updateProgress(log);
    }

    @Override
    public void finish(UUID id, FollowerFanoutStatus status, String error) {
        String trimmed = error == null || error.length() <= MAX_ERROR_LENGTH
                ? error
                : error.substring(0, MAX_ERROR_LENGTH);
        mapper.finish(id, status.name(), trimmed);
    }

    @Override
    public boolean isPushCoolingDown(UUID authorId, int minutes) {
        return minutes > 0 && mapper.existsPushWithinMinutes(authorId, minutes);
    }

    @Override
    public int countHeldBackSinceLastPush(UUID authorId) {
        return mapper.countHeldBackSinceLastPush(authorId);
    }
}

package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.MusicTrack;
import com.ds.goroute.mapper.MusicTrackMapper;
import com.ds.goroute.repository.MusicTrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MusicTrackRepositoryImpl implements MusicTrackRepository {

    private final MusicTrackMapper mapper;

    @Override
    public List<MusicTrack> findActive(int limit) {
        return mapper.findActive(limit);
    }

    @Override
    public List<MusicTrack> findActiveByIds(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : mapper.findActiveByIds(ids);
    }
}

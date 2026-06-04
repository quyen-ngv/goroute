package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ActivityBooking;
import com.ds.goroute.mapper.ActivityBookingGeoSearchParams;
import com.ds.goroute.mapper.ActivityBookingMapper;
import com.ds.goroute.repository.ActivityBookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ActivityBookingRepositoryImpl implements ActivityBookingRepository {

    private final ActivityBookingMapper mapper;

    @Override
    public void insert(ActivityBooking booking) {
        mapper.insert(booking);
    }

    @Override
    public Optional<ActivityBooking> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public List<ActivityBooking> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.findByIds(ids);
    }

    @Override
    public Optional<ActivityBooking> findByExternalId(String externalId) {
        return Optional.ofNullable(mapper.findByExternalId(externalId));
    }

    @Override
    public List<ActivityBooking> findPage(int limit, int offset) {
        return mapper.findAll(limit, offset);
    }

    @Override
    public long countAll() {
        return mapper.countAll();
    }

    @Override
    public List<ActivityBooking> findByDestinations(List<String> normalizedDestinationKeys, int limit, int offset) {
        return mapper.findByDestinations(normalizedDestinationKeys, limit, offset);
    }

    @Override
    public List<ActivityBooking> findWithinRadius(ActivityBookingGeoSearchParams params) {
        return mapper.findWithinRadius(params);
    }

    @Override
    public void update(ActivityBooking booking) {
        mapper.update(booking);
    }

    @Override
    public void delete(UUID id) {
        mapper.delete(id);
    }
}

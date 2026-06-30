package com.ds.goroute.repository;

import com.ds.goroute.entity.ActivityBooking;
import com.ds.goroute.mapper.ActivityBookingGeoSearchParams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityBookingRepository {

    void insert(ActivityBooking booking);

    Optional<ActivityBooking> findById(UUID id);
    
    List<ActivityBooking> findAll();

    List<ActivityBooking> findByIds(List<UUID> ids);

    Optional<ActivityBooking> findByExternalId(String externalId);

    List<ActivityBooking> findPage(int limit, int offset);

    long countAll();

    List<ActivityBooking> findByDestinations(List<String> normalizedDestinationKeys, int limit, int offset);

    List<ActivityBooking> findWithinRadius(ActivityBookingGeoSearchParams params);

    List<ActivityBooking> searchByKeyword(String keyword, int limit, int offset);

    void update(ActivityBooking booking);

    void delete(UUID id);
}

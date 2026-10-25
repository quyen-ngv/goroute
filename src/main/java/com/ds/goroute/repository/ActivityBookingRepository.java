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

    /** Imported catalogue rows among these ids (no partner organisation); any order. */
    List<ActivityBooking> findCatalogueByIds(java.util.Collection<UUID> ids);

    /** Feed search index: the next {@code limit} findable tours after {@code afterId} (null: from the start). */
    List<com.ds.goroute.entity.SocialSearchIndexRow> findSearchIndexPage(UUID afterId, int limit);

    /** Feed search index: tours changed after the cursor, each saying whether it is still findable. */
    List<com.ds.goroute.entity.SocialSearchIndexRow> findSearchIndexChanges(
            java.time.LocalDateTime afterUpdatedAt, UUID afterId, int limit);

    Optional<java.time.LocalDateTime> findSearchIndexMaxUpdatedAt();

    Optional<ActivityBooking> findByExternalId(String externalId);

    List<ActivityBooking> findPage(int limit, int offset);

    long countAll();

    List<ActivityBooking> findByDestinations(List<String> normalizedDestinationKeys, int limit, int offset);

    /** One page of the radius search, nearest first. */
    List<ActivityBooking> findWithinRadius(ActivityBookingGeoSearchParams params);

    /** Id and destinations of the bookings in the radius, nearest first, at most {@code params.limit}. */
    List<ActivityBooking> findRadiusCandidates(ActivityBookingGeoSearchParams params);

    /** List columns (no heavy JSON) for the given ids, unordered. */
    List<ActivityBooking> findListByIds(List<UUID> ids);

    List<ActivityBooking> searchByKeyword(String keyword, int limit, int offset);

    void update(ActivityBooking booking);

    void delete(UUID id);
}

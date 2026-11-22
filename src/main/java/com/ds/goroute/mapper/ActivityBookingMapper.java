package com.ds.goroute.mapper;

import com.ds.goroute.entity.ActivityBooking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ActivityBookingMapper {

    void insert(ActivityBooking booking);

    void update(ActivityBooking booking);

    ActivityBooking findById(@Param("id") UUID id);

    ActivityBooking findByExternalId(@Param("externalId") String externalId);

    List<ActivityBooking> findByIds(@Param("ids") List<UUID> ids);

    List<ActivityBooking> findCatalogueByIds(@Param("ids") java.util.Collection<UUID> ids);

    /** Feed search index: the next {@code limit} findable tours after {@code afterId}, by id. */
    List<com.ds.goroute.entity.SocialSearchIndexRow> selectSearchIndexPage(@Param("afterId") UUID afterId,
                                                                          @Param("limit") int limit);

    /** Feed search index: tours changed after the cursor, findable or not, oldest change first. */
    List<com.ds.goroute.entity.SocialSearchIndexRow> selectSearchIndexChanges(
            @Param("afterUpdatedAt") java.time.LocalDateTime afterUpdatedAt,
            @Param("afterId") UUID afterId,
            @Param("limit") int limit);

    java.time.LocalDateTime selectSearchIndexMaxUpdatedAt();

    List<ActivityBooking> findAll(@Param("limit") int limit, @Param("offset") int offset);

    long countAll();

    List<ActivityBooking> findByDestinations(@Param("destinations") List<String> destinations,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    List<ActivityBooking> findWithinRadius(@Param("p") ActivityBookingGeoSearchParams params);

    /** Only id and destinations are populated; nearest first, at most {@code params.limit} rows. */
    List<ActivityBooking> findRadiusCandidates(@Param("p") ActivityBookingGeoSearchParams params);

    List<ActivityBooking> findListByIds(@Param("ids") List<UUID> ids);

    List<ActivityBooking> searchByKeyword(@Param("keyword") String keyword,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    void delete(@Param("id") UUID id);
}

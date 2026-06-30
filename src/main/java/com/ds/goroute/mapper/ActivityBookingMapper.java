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

    List<ActivityBooking> findAll(@Param("limit") int limit, @Param("offset") int offset);

    long countAll();

    List<ActivityBooking> findByDestinations(@Param("destinations") List<String> destinations,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    List<ActivityBooking> findWithinRadius(@Param("p") ActivityBookingGeoSearchParams params);

    List<ActivityBooking> searchByKeyword(@Param("keyword") String keyword,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    void delete(@Param("id") UUID id);
}

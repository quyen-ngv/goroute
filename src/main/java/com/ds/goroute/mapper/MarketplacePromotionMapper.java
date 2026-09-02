package com.ds.goroute.mapper;

import com.ds.goroute.entity.RatePlanPromotion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface MarketplacePromotionMapper {
    int insert(RatePlanPromotion promotion);
    int update(RatePlanPromotion promotion);
    int delete(@Param("id") UUID id, @Param("hotelId") UUID hotelId);
    RatePlanPromotion findById(@Param("id") UUID id);
    List<RatePlanPromotion> findByHotel(@Param("hotelId") UUID hotelId, @Param("enabledOnly") boolean enabledOnly);
    /** Promotions that could apply to a stay: enabled, and their windows overlap the stay. */
    List<RatePlanPromotion> findApplicable(@Param("hotelId") UUID hotelId, @Param("ratePlanId") UUID ratePlanId,
                                           @Param("checkIn") java.time.LocalDate checkIn, @Param("checkOut") java.time.LocalDate checkOut,
                                           @Param("bookedOn") java.time.LocalDate bookedOn);
    int archiveForRatePlan(@Param("ratePlanId") UUID ratePlanId, @Param("actor") UUID actor, @Param("now") LocalDateTime now);
}

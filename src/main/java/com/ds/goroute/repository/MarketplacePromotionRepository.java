package com.ds.goroute.repository;

import com.ds.goroute.entity.RatePlanPromotion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplacePromotionRepository {
    int insert(RatePlanPromotion promotion);
    int update(RatePlanPromotion promotion);
    int delete(UUID id, UUID hotelId);
    Optional<RatePlanPromotion> findById(UUID id);
    List<RatePlanPromotion> findByHotel(UUID hotelId, boolean enabledOnly);
    List<RatePlanPromotion> findApplicable(UUID hotelId, UUID ratePlanId, LocalDate checkIn, LocalDate checkOut, LocalDate bookedOn);
    int archiveForRatePlan(UUID ratePlanId, UUID actor, LocalDateTime now);
}

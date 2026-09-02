package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.RatePlanPromotion;
import com.ds.goroute.mapper.MarketplacePromotionMapper;
import com.ds.goroute.repository.MarketplacePromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MarketplacePromotionRepositoryImpl implements MarketplacePromotionRepository {
    private final MarketplacePromotionMapper mapper;

    @Override public int insert(RatePlanPromotion promotion) { return mapper.insert(promotion); }
    @Override public int update(RatePlanPromotion promotion) { return mapper.update(promotion); }
    @Override public int delete(UUID id, UUID hotelId) { return mapper.delete(id, hotelId); }
    @Override public Optional<RatePlanPromotion> findById(UUID id) { return Optional.ofNullable(mapper.findById(id)); }
    @Override public List<RatePlanPromotion> findByHotel(UUID hotelId, boolean enabledOnly) { return mapper.findByHotel(hotelId, enabledOnly); }
    @Override public List<RatePlanPromotion> findApplicable(UUID hotelId, UUID ratePlanId, LocalDate checkIn, LocalDate checkOut, LocalDate bookedOn) {
        return mapper.findApplicable(hotelId, ratePlanId, checkIn, checkOut, bookedOn);
    }
    @Override public int archiveForRatePlan(UUID ratePlanId, UUID actor, LocalDateTime now) { return mapper.archiveForRatePlan(ratePlanId, actor, now); }
}

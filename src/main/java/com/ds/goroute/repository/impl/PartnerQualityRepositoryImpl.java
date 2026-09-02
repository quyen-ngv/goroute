package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.PartnerBookingAggregate;
import com.ds.goroute.entity.PartnerQualitySnapshot;
import com.ds.goroute.entity.PartnerReviewAggregate;
import com.ds.goroute.mapper.PartnerQualityMapper;
import com.ds.goroute.repository.PartnerQualityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PartnerQualityRepositoryImpl implements PartnerQualityRepository {
    private final PartnerQualityMapper mapper;

    @Override public PartnerBookingAggregate aggregateHotelBookings(UUID organizationId, LocalDateTime since, int slaMinutes) {
        return mapper.aggregateHotelBookings(organizationId, since, slaMinutes);
    }
    @Override public PartnerBookingAggregate aggregateActivityOrders(UUID organizationId, LocalDateTime since, int slaMinutes) {
        return mapper.aggregateActivityOrders(organizationId, since, slaMinutes);
    }
    @Override public PartnerReviewAggregate aggregateReviews(UUID organizationId, LocalDateTime since) {
        return mapper.aggregateReviews(organizationId, since);
    }
    @Override public int upsertSnapshot(PartnerQualitySnapshot snapshot) { return mapper.upsertSnapshot(snapshot); }
    @Override public Optional<PartnerQualitySnapshot> findSnapshot(UUID organizationId, int windowDays) {
        return Optional.ofNullable(mapper.findSnapshot(organizationId, windowDays));
    }
}

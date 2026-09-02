package com.ds.goroute.repository;

import com.ds.goroute.entity.PartnerBookingAggregate;
import com.ds.goroute.entity.PartnerQualitySnapshot;
import com.ds.goroute.entity.PartnerReviewAggregate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PartnerQualityRepository {
    PartnerBookingAggregate aggregateHotelBookings(UUID organizationId, LocalDateTime since, int slaMinutes);
    PartnerBookingAggregate aggregateActivityOrders(UUID organizationId, LocalDateTime since, int slaMinutes);
    PartnerReviewAggregate aggregateReviews(UUID organizationId, LocalDateTime since);
    int upsertSnapshot(PartnerQualitySnapshot snapshot);
    Optional<PartnerQualitySnapshot> findSnapshot(UUID organizationId, int windowDays);
}

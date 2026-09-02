package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PartnerQualityResponse;
import com.ds.goroute.entity.PartnerBookingAggregate;
import com.ds.goroute.entity.PartnerQualitySnapshot;
import com.ds.goroute.entity.PartnerReviewAggregate;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.PartnerQualityRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.PartnerQualityBadgeRule;
import com.ds.goroute.service.PartnerQualityService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PartnerQualityBadge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerQualityServiceImpl implements PartnerQualityService {
    /** One rolling window is kept per organization; the table allows more if a second one is ever needed. */
    static final int WINDOW_DAYS = 90;

    private final PartnerQualityRepository repository;
    private final HostOrganizationRepository organizations;
    private final PartnerAuthorizationService authorization;
    private final BusinessConfigService businessConfig;

    @Override
    @Transactional
    public PartnerQualityResponse compute(UUID organizationId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(WINDOW_DAYS);
        int slaMinutes = businessConfig.getInt(BusinessConfigKey.PARTNER_RESPONSE_SLA_MINUTES);
        PartnerBookingAggregate hotels = orEmpty(repository.aggregateHotelBookings(organizationId, since, slaMinutes));
        PartnerBookingAggregate activities = orEmpty(repository.aggregateActivityOrders(organizationId, since, slaMinutes));
        PartnerReviewAggregate reviews = repository.aggregateReviews(organizationId, since);

        int total = value(hotels.getTotal()) + value(activities.getTotal());
        int confirmed = value(hotels.getConfirmed()) + value(activities.getConfirmed());
        int cancelledByHost = value(hotels.getCancelledByHost()) + value(activities.getCancelledByHost());
        int noShow = value(hotels.getNoShow()) + value(activities.getNoShow());
        int expired = value(hotels.getExpired()) + value(activities.getExpired());
        int responded = value(hotels.getResponded()) + value(activities.getResponded());
        int withinSla = value(hotels.getRespondedWithinSla()) + value(activities.getRespondedWithinSla());
        BigDecimal responseMinutes = orZero(hotels.getResponseMinutesSum()).add(orZero(activities.getResponseMinutesSum()));

        BigDecimal hostCancellationRate = rate(cancelledByHost, total);
        BigDecimal noShowRate = rate(noShow, total);
        BigDecimal expiryRate = rate(expired, total);
        BigDecimal slaRate = rate(withinSla, responded);
        Integer avgResponse = responded == 0 ? null
                : responseMinutes.divide(BigDecimal.valueOf(responded), 0, RoundingMode.HALF_UP).intValue();
        int reviewCount = reviews == null ? 0 : value(reviews.getReviewCount());
        BigDecimal reviewAverage = reviews == null || reviews.getReviewAverage() == null || reviewCount == 0 ? null
                : reviews.getReviewAverage().setScale(2, RoundingMode.HALF_UP);
        PartnerQualityBadge badge = PartnerQualityBadgeRule.evaluate(total, hostCancellationRate, noShowRate,
                expiryRate, slaRate, reviewAverage);

        PartnerQualitySnapshot snapshot = PartnerQualitySnapshot.builder()
                .id(UUID.randomUUID()).organizationId(organizationId).windowDays(WINDOW_DAYS).computedAt(now)
                .bookingsTotal(total).bookingsConfirmed(confirmed).bookingsCancelledByHost(cancelledByHost)
                .bookingsNoShow(noShow).bookingsExpired(expired).avgResponseMinutes(avgResponse)
                .responseWithinSlaRate(slaRate).hostCancellationRate(hostCancellationRate).noShowRate(noShowRate)
                .expiryRate(expiryRate).reviewCount(reviewCount).reviewAverage(reviewAverage).badge(badge.name()).build();
        repository.upsertSnapshot(snapshot);
        return toResponse(organizationId, repository.findSnapshot(organizationId, WINDOW_DAYS).orElse(snapshot));
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerQualityResponse get(UUID organizationId) {
        return toResponse(organizationId, repository.findSnapshot(organizationId, WINDOW_DAYS).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerQualityResponse partnerGet(UUID actorUserId, UUID organizationId) {
        authorization.requirePermission(organizationId, actorUserId, "ORGANIZATION_READ");
        return get(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerQualityResponse adminGet(UUID organizationId) {
        requireOrganization(organizationId);
        return get(organizationId);
    }

    @Override
    @Transactional
    public PartnerQualityResponse adminRecompute(UUID organizationId) {
        requireOrganization(organizationId);
        return compute(organizationId);
    }

    private void requireOrganization(UUID organizationId) {
        organizations.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Partner organization not found"));
    }

    private PartnerQualityResponse toResponse(UUID organizationId, PartnerQualitySnapshot snapshot) {
        if (snapshot == null) {
            return PartnerQualityResponse.builder().organizationId(organizationId).windowDays(WINDOW_DAYS)
                    .badge(PartnerQualityBadge.NONE.name()).build();
        }
        return PartnerQualityResponse.builder().organizationId(organizationId).windowDays(snapshot.getWindowDays())
                .computedAt(snapshot.getComputedAt()).bookingsTotal(snapshot.getBookingsTotal())
                .bookingsConfirmed(snapshot.getBookingsConfirmed()).hostCancellationRate(snapshot.getHostCancellationRate())
                .noShowRate(snapshot.getNoShowRate()).expiryRate(snapshot.getExpiryRate())
                .avgResponseMinutes(snapshot.getAvgResponseMinutes()).responseWithinSlaRate(snapshot.getResponseWithinSlaRate())
                .reviewCount(snapshot.getReviewCount()).reviewAverage(snapshot.getReviewAverage())
                .badge(snapshot.getBadge()).build();
    }

    /** Percentage with two decimals, {@code null} when there is nothing to divide by. */
    private static BigDecimal rate(int part, int whole) {
        if (whole <= 0) return null;
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    private static PartnerBookingAggregate orEmpty(PartnerBookingAggregate value) {
        return value == null ? PartnerBookingAggregate.builder().build() : value;
    }

    private static int value(Integer value) { return value == null ? 0 : value; }
    private static BigDecimal orZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}

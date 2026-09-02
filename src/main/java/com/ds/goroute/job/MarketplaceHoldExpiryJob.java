package com.ds.goroute.job;

import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.HotelMarketplaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Releases held inventory for booking requests the partner never answered.
 *
 * <p>Each booking/order is expired in its own transaction (the service method is called through
 * the Spring proxy, so its {@code @Transactional} applies per row). A row with inconsistent
 * inventory therefore only fails itself; before this isolation a single bad row rolled back the
 * whole batch and, because it always sorted first, wedged expiry for everything behind it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketplaceHoldExpiryJob {
    static final int BATCH_SIZE = 100;
    private final HotelMarketplaceService hotelMarketplaceService;
    private final ActivityCommerceService activityCommerceService;

    @Scheduled(fixedDelayString = "${goroute.marketplace.hold-expiry-delay-ms:60000}")
    public void releaseExpiredPaymentHolds() {
        LocalDateTime now = LocalDateTime.now();
        int hotelCount = expireEach("hotel booking", hotelMarketplaceService.findExpiredPendingBookingIds(now, BATCH_SIZE),
                hotelMarketplaceService::expirePendingBooking, now);
        int activityCount = expireEach("activity order", activityCommerceService.findExpiredPendingOrderIds(now, BATCH_SIZE),
                activityCommerceService::expirePendingOrder, now);
        if (hotelCount + activityCount > 0) {
            log.info("Released {} expired hotel holds and {} expired activity holds", hotelCount, activityCount);
        }
    }

    int expireEach(String kind, List<UUID> ids, BiPredicate<UUID, LocalDateTime> expire, LocalDateTime now) {
        int expired = 0;
        for (UUID id : ids) {
            try {
                if (expire.test(id, now)) expired++;
            } catch (RuntimeException ex) {
                // Keep going: the row stays PENDING and is retried on the next run, and it must not
                // block the rows behind it. Operators find it by this log line and the booking id.
                log.error("Could not expire {} {}: {}", kind, id, ex.getMessage());
            }
        }
        return expired;
    }
}

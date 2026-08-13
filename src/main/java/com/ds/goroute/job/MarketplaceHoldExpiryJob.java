package com.ds.goroute.job;

import com.ds.goroute.service.impl.ActivityCommerceServiceImpl;
import com.ds.goroute.service.impl.HotelMarketplaceServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketplaceHoldExpiryJob {
    private final HotelMarketplaceServiceImpl hotelMarketplaceService;
    private final ActivityCommerceServiceImpl activityCommerceService;

    @Scheduled(fixedDelayString = "${goroute.marketplace.hold-expiry-delay-ms:60000}")
    public void releaseExpiredPaymentHolds() {
        int hotelCount = hotelMarketplaceService.expirePendingPaymentHolds();
        int activityCount = activityCommerceService.expirePendingPaymentHolds();
        if (hotelCount + activityCount > 0) log.info("Released {} expired hotel holds and {} expired activity holds", hotelCount, activityCount);
    }
}

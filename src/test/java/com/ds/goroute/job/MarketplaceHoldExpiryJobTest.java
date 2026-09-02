package com.ds.goroute.job;

import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.HotelMarketplaceService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceHoldExpiryJobTest {
    @Test
    void oneInconsistentRowDoesNotBlockTheRestOfTheBatchNorTheOtherProductLine() {
        HotelMarketplaceService hotels = mock(HotelMarketplaceService.class);
        ActivityCommerceService activities = mock(ActivityCommerceService.class);
        UUID broken = UUID.randomUUID(); UUID fine = UUID.randomUUID(); UUID order = UUID.randomUUID();
        when(hotels.findExpiredPendingBookingIds(any(), anyInt())).thenReturn(List.of(broken, fine));
        when(hotels.expirePendingBooking(eq(broken), any())).thenThrow(new IllegalStateException("inventory inconsistent"));
        when(hotels.expirePendingBooking(eq(fine), any())).thenReturn(true);
        when(activities.findExpiredPendingOrderIds(any(), anyInt())).thenReturn(List.of(order));
        when(activities.expirePendingOrder(eq(order), any())).thenReturn(true);

        new MarketplaceHoldExpiryJob(hotels, activities).releaseExpiredPaymentHolds();

        verify(hotels).expirePendingBooking(eq(fine), any(LocalDateTime.class));
        verify(activities).expirePendingOrder(eq(order), any(LocalDateTime.class));
    }

    @Test
    void countsOnlyRowsThatWereActuallyExpired() {
        HotelMarketplaceService hotels = mock(HotelMarketplaceService.class);
        UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID();
        when(hotels.expirePendingBooking(eq(a), any())).thenReturn(true);
        when(hotels.expirePendingBooking(eq(b), any())).thenReturn(false);
        MarketplaceHoldExpiryJob job = new MarketplaceHoldExpiryJob(hotels, mock(ActivityCommerceService.class));
        assertEquals(1, job.expireEach("hotel booking", List.of(a, b), hotels::expirePendingBooking, LocalDateTime.now()));
    }
}

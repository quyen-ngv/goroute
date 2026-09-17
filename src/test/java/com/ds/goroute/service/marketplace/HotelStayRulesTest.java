package com.ds.goroute.service.marketplace;

import com.ds.goroute.entity.HotelAvailabilityDay;
import com.ds.goroute.entity.RoomType;
import com.ds.goroute.type.HotelOfferUnavailableReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HotelStayRulesTest {
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("An open calendar with enough units is bookable")
    void openCalendarIsBookable() {
        assertThat(HotelStayRules.violation(twoNights(night(0, 3), night(1, 2)), 2, 2, 5)).isEmpty();
        assertThat(HotelStayRules.minimumAvailableUnits(twoNights(night(0, 3), night(1, 2)))).isEqualTo(2);
    }

    @Test
    @DisplayName("A missing night means the rate is not open for the whole stay")
    void missingNightIsNotOpen() {
        assertThat(HotelStayRules.violation(List.of(night(0, 3)), 2, 1, 5)).contains(HotelOfferUnavailableReason.NOT_OPEN);
    }

    @Test
    @DisplayName("Fewer units than rooms requested, or stop-sell, is sold out")
    void soldOut() {
        assertThat(HotelStayRules.violation(twoNights(night(0, 3), night(1, 1)), 2, 2, 5)).contains(HotelOfferUnavailableReason.SOLD_OUT);
        HotelAvailabilityDay stopped = night(1, 5);
        stopped.setStopSell(true);
        assertThat(HotelStayRules.violation(twoNights(night(0, 5), stopped), 2, 1, 5)).contains(HotelOfferUnavailableReason.SOLD_OUT);
    }

    @Test
    @DisplayName("Arrival/departure restrictions, stay length and advance window are reported in that order")
    void restrictions() {
        HotelAvailabilityDay arrival = night(0, 5);
        arrival.setClosedToArrival(true);
        assertThat(HotelStayRules.violation(twoNights(arrival, night(1, 5)), 2, 1, 5)).contains(HotelOfferUnavailableReason.ARRIVAL_CLOSED);

        HotelAvailabilityDay minStay = night(0, 5);
        minStay.setMinStay(3);
        assertThat(HotelStayRules.violation(twoNights(minStay, night(1, 5)), 2, 1, 5)).contains(HotelOfferUnavailableReason.STAY_LENGTH);

        HotelAvailabilityDay advance = night(0, 5);
        advance.setMinAdvanceDays(7);
        assertThat(HotelStayRules.violation(twoNights(advance, night(1, 5)), 2, 1, 5)).contains(HotelOfferUnavailableReason.BOOKING_WINDOW);
    }

    @Test
    @DisplayName("Party must fit adults, children and total occupancy across the rooms")
    void capacity() {
        RoomType room = RoomType.builder().maxAdults(2).maxChildren(1).maxOccupancy(3).build();
        assertThat(HotelStayRules.fits(room, 1, 2, 1)).isTrue();
        assertThat(HotelStayRules.fits(room, 1, 3, 0)).isFalse();
        assertThat(HotelStayRules.fits(room, 2, 3, 2)).isTrue();
    }

    private static List<HotelAvailabilityDay> twoNights(HotelAvailabilityDay first, HotelAvailabilityDay second) {
        return List.of(first, second);
    }

    private static HotelAvailabilityDay night(int offset, int units) {
        return HotelAvailabilityDay.builder().inventoryDate(CHECK_IN.plusDays(offset)).availableUnits(units)
                .stopSell(false).closedToArrival(false).closedToDeparture(false).minStay(1)
                .nightlyPrice(new BigDecimal("1000000")).build();
    }
}

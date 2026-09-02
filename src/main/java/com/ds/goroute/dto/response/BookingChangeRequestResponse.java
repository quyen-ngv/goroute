package com.ds.goroute.dto.response;

import com.ds.goroute.entity.BookingChangeRequest;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class BookingChangeRequestResponse {
    UUID id;
    String bookingType;
    UUID hotelBookingId;
    UUID activityOrderId;
    UUID organizationId;
    String bookingCode;
    String status;
    LocalDate newCheckInDate;
    LocalDate newCheckOutDate;
    Integer newAdults;
    Integer newChildren;
    UUID newSlotId;
    LocalDateTime newSlotStartsAt;
    String message;
    String responseNote;
    BigDecimal priceBefore;
    BigDecimal priceAfter;
    String currency;
    LocalDateTime createdAt;
    LocalDateTime respondedAt;

    public static BookingChangeRequestResponse from(BookingChangeRequest r) {
        return BookingChangeRequestResponse.builder().id(r.getId()).bookingType(r.getBookingType()).hotelBookingId(r.getHotelBookingId())
                .activityOrderId(r.getActivityOrderId()).organizationId(r.getOrganizationId()).bookingCode(r.getBookingCode()).status(r.getStatus())
                .newCheckInDate(r.getNewCheckInDate()).newCheckOutDate(r.getNewCheckOutDate()).newAdults(r.getNewAdults()).newChildren(r.getNewChildren())
                .newSlotId(r.getNewSlotId()).newSlotStartsAt(r.getNewSlotStartsAt()).message(r.getMessage()).responseNote(r.getResponseNote())
                .priceBefore(r.getPriceBefore()).priceAfter(r.getPriceAfter()).currency(r.getCurrency()).createdAt(r.getCreatedAt()).respondedAt(r.getRespondedAt()).build();
    }
}

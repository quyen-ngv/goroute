package com.ds.goroute.service.scheduledmessage;

import com.ds.goroute.entity.ScheduledMessageTarget;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduledMessagePlaceholdersTest {

    private static ScheduledMessageTarget stay() {
        return ScheduledMessageTarget.builder()
                .bookingType("HOTEL").guestName("Nguyễn Văn A").bookingCode("HB-1024")
                .propertyName("Khách sạn Biển Xanh")
                .checkInDate(LocalDate.of(2026, 9, 12)).checkOutDate(LocalDate.of(2026, 9, 15))
                .build();
    }

    @Test
    void substitutesEveryTokenTheBookingHasAValueFor() {
        String body = "Chào {guestName}, đơn {bookingCode} tại {propertyName} nhận phòng {checkInDate}, trả phòng {checkOutDate}.";
        assertEquals("Chào Nguyễn Văn A, đơn HB-1024 tại Khách sạn Biển Xanh nhận phòng 12/09/2026, trả phòng 15/09/2026.",
                ScheduledMessagePlaceholders.apply(body, stay()));
    }

    @Test
    void unknownPlaceholderIsLeftAsWrittenAndNeverBlocksTheMessage() {
        // A rule author invents {roomNumber}; the guest still gets the rest of the message.
        assertEquals("Phòng {roomNumber} của HB-1024 đã sẵn sàng.",
                ScheduledMessagePlaceholders.apply("Phòng {roomNumber} của {bookingCode} đã sẵn sàng.", stay()));
    }

    @Test
    void tokenTheBookingHasNoValueForSurvivesRatherThanBecomingEmpty() {
        // A stay has no slot start. Leaving the token is honest; an empty gap reads like a bug.
        assertEquals("Bắt đầu lúc {slotStartsAt}",
                ScheduledMessagePlaceholders.apply("Bắt đầu lúc {slotStartsAt}", stay()));
        assertEquals("Xin chào {guestName}",
                ScheduledMessagePlaceholders.apply("Xin chào {guestName}",
                        ScheduledMessageTarget.builder().bookingType("HOTEL").guestName("   ").build()));
    }

    @Test
    void activityTargetFormatsTheSlotStartWithItsTime() {
        ScheduledMessageTarget order = ScheduledMessageTarget.builder()
                .bookingType("ACTIVITY").bookingCode("AO-77").propertyName("Tour Sơn Trà")
                .slotStartsAt(LocalDateTime.of(2026, 9, 12, 7, 30))
                .build();
        assertEquals("Tour Sơn Trà (AO-77) khởi hành 12/09/2026 07:30",
                ScheduledMessagePlaceholders.apply("{propertyName} ({bookingCode}) khởi hành {slotStartsAt}", order));
    }

    @Test
    void repeatedTokenIsSubstitutedEveryTimeAndBlankBodyIsReturnedUnchanged() {
        assertEquals("HB-1024 / HB-1024",
                ScheduledMessagePlaceholders.apply("{bookingCode} / {bookingCode}", stay()));
        assertEquals("", ScheduledMessagePlaceholders.apply("", stay()));
        assertEquals("no target", ScheduledMessagePlaceholders.apply("no target", null));
    }
}

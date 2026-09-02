package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** Request bodies of the change-request flow, grouped because they are tiny and always used together. */
public final class BookingChangeRequests {
    private BookingChangeRequests() {}

    @Data
    public static class CreateHotelChange {
        @NotNull private LocalDate checkInDate;
        @NotNull private LocalDate checkOutDate;
        @Min(1) @Max(40) private Integer adults;
        @Min(0) @Max(40) private Integer children;
        @Size(max = 1000) private String message;
    }

    @Data
    public static class CreateActivityChange {
        @NotNull private UUID slotId;
        @Size(max = 1000) private String message;
    }

    @Data
    public static class Decide {
        private boolean accept;
        @Size(max = 1000) private String note;
        /** data_version of the booking/order, so an accept never lands on a booking someone else just changed. */
        private Long expectedVersion;
    }
}

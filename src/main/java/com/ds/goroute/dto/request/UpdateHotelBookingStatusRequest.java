package com.ds.goroute.dto.request;

import com.ds.goroute.type.MarketplaceBookingStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateHotelBookingStatusRequest {
    @NotNull
    private MarketplaceBookingStatus bookingStatus;
    private String reason;
    private Long expectedVersion;
    /** Only meaningful with NO_SHOW: whether the property charged the guest (drives commission later). */
    private Boolean guestCharged;
}

package com.ds.goroute.dto.request;

import com.ds.goroute.type.MarketplaceBookingStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateActivityOrderStatusRequest {
    @NotNull private MarketplaceBookingStatus orderStatus;
    private String reason;
    private Long expectedVersion;
}

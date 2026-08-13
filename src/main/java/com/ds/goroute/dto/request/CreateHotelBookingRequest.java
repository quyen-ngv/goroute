package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateHotelBookingRequest {
    @NotNull private UUID hotelId;
    @NotNull private UUID roomTypeId;
    @NotNull private UUID ratePlanId;
    @NotNull private LocalDate checkInDate;
    @NotNull private LocalDate checkOutDate;
    @Min(1) private Integer quantity = 1;
    @Min(1) private Integer adults = 1;
    @Min(0) private Integer children = 0;
    @NotNull private Map<String, Object> guestLead;
    private List<Map<String, Object>> guestDetails;
    private String specialRequests;
    private LocalTime estimatedArrivalTime;
    @Size(max = 120) private String idempotencyKey;
}

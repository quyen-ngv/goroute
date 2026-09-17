package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * One selection to park in the cart. Which fields are required depends on {@code itemType};
 * the service validates the shape, because bean validation cannot express "either this group
 * or that group" without splitting the endpoint in two.
 */
@Data
public class AddCartItemRequest {
    /** HOTEL or ACTIVITY. */
    @NotNull private String itemType;

    private UUID hotelId;
    private UUID roomTypeId;
    private UUID ratePlanId;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    @Min(1) private Integer quantity;
    @Min(1) private Integer adults;
    @Min(0) private Integer children;

    private UUID activityId;
    private UUID packageId;
    private UUID slotId;
    private Map<String, @Min(0) Integer> unitQuantities;

    @Size(max = 2000) private String specialRequests;
}

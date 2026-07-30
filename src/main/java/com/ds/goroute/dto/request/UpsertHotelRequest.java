package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class UpsertHotelRequest {
    @NotNull private UUID organizationId;
    @NotNull private UUID placeId;
    @Size(max = 100) private String propertyCode;
    @Pattern(regexp = "HOTEL|RESORT|HOSTEL|APARTMENT|VILLA|HOMESTAY|GUEST_HOUSE")
    private String propertyType = "HOTEL";
    @Min(1) @Max(5) private Integer starRating;
    private String description;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    @Size(max = 100) private String timezone;
    private List<String> amenities;
    private Map<String, Object> policies;
    private Map<String, Object> bookingContact;
    @Pattern(regexp = "DRAFT|ENABLED|DISABLED|ARCHIVED") private String status;
    private String disabledReason;
    private Long expectedVersion;
}

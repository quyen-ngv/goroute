package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripDestinationRequest {
    @jakarta.validation.constraints.Size(max = 255)
    private String name;
    @NotNull(message = "Location image ID is required")
    private UUID locationImageId;

    @NotNull(message = "Destination start date is required")
    private LocalDate startDate;

    @NotNull(message = "Destination end date is required")
    private LocalDate endDate;

    @NotNull(message = "Destination latitude is required")
    private BigDecimal latitude;

    @NotNull(message = "Destination longitude is required")
    private BigDecimal longitude;

    private Integer orderIndex;
}

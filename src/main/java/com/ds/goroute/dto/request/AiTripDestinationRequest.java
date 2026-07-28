package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripDestinationRequest {
    @NotNull(message = "Location image ID is required")
    private UUID locationImageId;

    @NotNull(message = "Destination start date is required")
    private LocalDate startDate;

    @NotNull(message = "Destination end date is required")
    private LocalDate endDate;

    private Integer orderIndex;
}

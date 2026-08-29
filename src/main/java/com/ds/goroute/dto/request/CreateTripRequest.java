package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTripRequest {
    @NotBlank(message = "Trip name is required")
    @Size(max = 255)
    @ModeratedText(contentType = ModeratedContentType.TRIP, visibility = ModerationVisibility.PUBLIC)
    private String name;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private String destination;
    private String destinationPlaceId;
    private BigDecimal destinationLat;
    private BigDecimal destinationLng;
    private List<TripDestinationRequest> destinations;
    private BigDecimal budget;
    private String currency;
}

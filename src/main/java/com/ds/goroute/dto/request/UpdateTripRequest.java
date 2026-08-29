package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTripRequest {
    @ModeratedText(contentType = ModeratedContentType.TRIP, visibility = ModerationVisibility.PUBLIC)
    private String name;
    private String coverImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private String destination;
    private BigDecimal destinationLat;
    private BigDecimal destinationLng;
    private List<TripDestinationRequest> destinations;
    private BigDecimal budget;
    private String currency;
    private String status;
    private String visibility;
    @ModeratedText(contentType = ModeratedContentType.TRIP, visibility = ModerationVisibility.GROUP)
    private String notes;
    @ModeratedText(contentType = ModeratedContentType.TRIP, visibility = ModerationVisibility.PUBLIC)
    private String description;
    private Boolean shareExpenses;
    private Boolean shareNotes;

    // Starting point
    private String startingPointName;
    private String startingPointAddress;
    private BigDecimal startingPointLat;
    private BigDecimal startingPointLng;
    private LocalDateTime startingPointTime;
}

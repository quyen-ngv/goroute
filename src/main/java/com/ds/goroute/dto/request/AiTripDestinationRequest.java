package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.utils.json.LenientUuidDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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
    @ModeratedText(contentType = ModeratedContentType.TRIP, visibility = ModerationVisibility.PUBLIC)
    private String name;
    /**
     * Location image the destination was picked from; null for a place found by map search
     * (anywhere abroad), whose Goong/Mapbox ID arrives here and is dropped. Only the legacy V1
     * draft flow requires it.
     */
    @JsonDeserialize(using = LenientUuidDeserializer.class)
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

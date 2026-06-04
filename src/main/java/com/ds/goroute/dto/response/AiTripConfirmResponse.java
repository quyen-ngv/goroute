package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripConfirmResponse {
    private TripResponse trip;
    private Integer selectedCount;
    private Integer scheduledCount;
    private Integer filledDays;
    private Integer totalDays;
    private String coverageMessage;
    /** Full explanation for popup and trip description. */
    private String generationSummary;
    /** Names of selected places that could not fit in the schedule. */
    private List<String> skippedPlaceNames;
}

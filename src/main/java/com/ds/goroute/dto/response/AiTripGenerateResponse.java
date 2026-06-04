package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripGenerateResponse {
    private UUID draftId;
    private String tier;
    private Integer aiTripsUsed;
    private Integer aiTripLimit;
    private List<AiTripCandidateResponse> candidates;
}

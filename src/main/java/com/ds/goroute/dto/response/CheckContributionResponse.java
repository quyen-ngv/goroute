package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckContributionResponse {

    private boolean exists;
    private String matchType;
    private ExistingPlaceSummary existingPlace;
    private UUID pendingContributionGroupId;
    private boolean canContributeReview;
    private String message;
}

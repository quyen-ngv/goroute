package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
public class ContributionImportRequest {

    private String jobId;
    private UUID contributionGroupId;
    private UUID gorouteJobId;
    private Boolean placeAlreadyExists;
    private String existingGooglePlaceId;

    @Valid
    private ImportPlaceRequest place;

    @Valid
    private List<GorouteContributionReviewInput> gorouteReviews;

    private List<UUID> contributorUserIds;

    private Boolean skipPlaceInsertIfExists;
}

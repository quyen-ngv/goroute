package com.ds.goroute.service;

import com.ds.goroute.dto.request.CheckContributionRequest;
import com.ds.goroute.dto.request.ContributionImportRequest;
import com.ds.goroute.dto.request.CreateContributionRequest;
import com.ds.goroute.dto.response.*;

import java.util.List;
import java.util.UUID;

public interface PlaceContributionService {

    CheckContributionResponse checkContribution(CheckContributionRequest request);

    ContributionResponse createContribution(UUID userId, CreateContributionRequest request);

    void cancelContribution(UUID userId, UUID contributionId);

    List<ContributionResponse> getMyContributions(UUID userId, int page, int size);

    List<ContributedPlaceResponse> getMyContributedPlaces(UUID userId, int page, int size);

    ContributionImportResponse importContribution(ContributionImportRequest request);

    ContributionImportResponse getImportResult(UUID contributionGroupId, UUID gorouteJobId);

    List<AdminContributionGroupResponse> adminListGroups(String status, int page, int size);

    AdminContributionGroupResponse adminGetGroup(UUID groupId);

    void adminApprove(UUID groupId);

    void adminReject(UUID groupId, String reason);

    List<ContributorSummaryResponse> getPlaceContributors(UUID placeId);

    void syncScrapingGroup(UUID groupId);
}

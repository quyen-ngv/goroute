package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateActivityPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateActivityPlaceProposalsRequest;
import com.ds.goroute.dto.request.CreateCheckinPlaceProposalsRequest;
import com.ds.goroute.dto.request.CreateAdminLinksPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateManualPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateSocialPlaceImportJobRequest;
import com.ds.goroute.dto.response.AdminActivityPlaceProposalRunResponse;
import com.ds.goroute.dto.response.PlaceImportJobResponse;
import com.ds.goroute.dto.response.AdminPlaceImportMappingResponse;
import com.ds.goroute.dto.response.AdminPlaceImportRunResponse;

import java.util.List;
import java.util.UUID;

public interface PlaceImportJobService {
    PlaceImportJobResponse createFromSocialJobs(UUID userId, CreateSocialPlaceImportJobRequest request);

    PlaceImportJobResponse createFromActivities(UUID userId, CreateActivityPlaceImportJobRequest request);

    PlaceImportJobResponse get(UUID userId, UUID jobId);

    List<PlaceImportJobResponse> listMine(UUID userId, int page, int size);

    AdminPlaceImportRunResponse adminRunSocialJobs(CreateSocialPlaceImportJobRequest request);

    AdminPlaceImportRunResponse adminRunActivityJobs(CreateActivityPlaceImportJobRequest request);

    PlaceImportJobResponse adminRunManualPlaceImport(CreateManualPlaceImportJobRequest request);

    PlaceImportJobResponse adminRunLinkImport(CreateAdminLinksPlaceImportJobRequest request);

    /** Imports each proposed place now; the activity is linked only when the mapping is approved. */
    AdminActivityPlaceProposalRunResponse adminRunActivityProposals(CreateActivityPlaceProposalsRequest request);

    /** Agent proposals for check-ins with no catalogue place; each waits for a human to approve. */
    AdminActivityPlaceProposalRunResponse adminRunCheckinProposals(CreateCheckinPlaceProposalsRequest request);

    List<PlaceImportJobResponse> adminListJobs(UUID userId, String status, String sourceType, int page, int size);

    PlaceImportJobResponse adminGetJob(UUID jobId);

    List<AdminPlaceImportMappingResponse> adminListMappings(String approvalStatus, String sourceType, int page, int size);

    AdminPlaceImportMappingResponse adminApproveMapping(UUID itemId, String note, UUID adminId);

    void adminRejectMapping(UUID itemId, String note);
}

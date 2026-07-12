package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateActivityPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateManualPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateSocialPlaceImportJobRequest;
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

    List<PlaceImportJobResponse> adminListJobs(UUID userId, String status, int page, int size);

    PlaceImportJobResponse adminGetJob(UUID jobId);

    List<AdminPlaceImportMappingResponse> adminListMappings(String approvalStatus, int page, int size);

    AdminPlaceImportMappingResponse adminApproveMapping(UUID itemId, String note);

    void adminRejectMapping(UUID itemId, String note);
}

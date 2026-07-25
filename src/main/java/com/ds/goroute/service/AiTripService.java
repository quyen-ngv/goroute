package com.ds.goroute.service;

import com.ds.goroute.dto.request.AiTripConfirmRequest;
import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.dto.response.AiTripConfirmResponse;
import com.ds.goroute.dto.response.AiTripGenerateResponse;
import com.ds.goroute.dto.response.AiTripUsage;

import java.util.UUID;

public interface AiTripService {
    AiTripUsage getEligibility(UUID userId);

    AiTripGenerateResponse generateCandidates(AiTripGenerateRequest request, UUID userId);

    AiTripConfirmResponse confirmTrip(UUID draftId, AiTripConfirmRequest request, UUID userId);
}

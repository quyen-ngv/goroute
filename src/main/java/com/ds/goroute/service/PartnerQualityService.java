package com.ds.goroute.service;

import com.ds.goroute.dto.response.PartnerQualityResponse;

import java.util.UUID;

/** Rolling-window quality snapshot per partner organization (see {@code PartnerQualityJob}). */
public interface PartnerQualityService {
    /** Recomputes and stores the snapshot for one organization; safe to call repeatedly. */
    PartnerQualityResponse compute(UUID organizationId);
    /** Last stored snapshot, or an empty response (null figures) when never computed. */
    PartnerQualityResponse get(UUID organizationId);
    PartnerQualityResponse partnerGet(UUID actorUserId, UUID organizationId);
    PartnerQualityResponse adminGet(UUID organizationId);
    PartnerQualityResponse adminRecompute(UUID organizationId);
}

package com.ds.goroute.service;

import com.ds.goroute.dto.response.PartnerOrganizationSummaryResponse;

import java.util.UUID;

public interface PartnerDashboardService {
    PartnerOrganizationSummaryResponse summary(UUID actorUserId, UUID organizationId);
}

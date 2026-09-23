package com.ds.goroute.partneronboarding.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * The little a wizard or an app entry point needs to know about a business: what to call it
 * and whether it has been approved yet. Deliberately not {@code HostOrganizationResponse} —
 * commission rate and billing details have no business on a sign-up screen.
 */
@Data
@Builder
public class OnboardingOrganizationResponse {

    private UUID id;
    private String displayName;
    private String verificationStatus;
    private String operationalStatus;
    /** True when the caller owns the organization; owners are never an {@code organization_members} row. */
    private boolean owner;
}

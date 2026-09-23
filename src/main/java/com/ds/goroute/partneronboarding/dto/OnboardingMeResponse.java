package com.ds.goroute.partneronboarding.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One call that answers everything a client needs before showing a partner entry point:
 * is the feature on, am I a partner already, and did I leave something unfinished.
 *
 * <p>The app asks this instead of reading public config plus {@code /partner/organizations}
 * plus a draft list, because on a phone that is three round trips before a row can decide
 * whether to render.
 */
@Data
@Builder
public class OnboardingMeResponse {

    /** The listing wizard is available. */
    private boolean enabled;
    /** The partner workspace is available inside the mobile app. */
    private boolean partnerAppEnabled;
    /** Businesses the caller owns or is an active member of. Empty means "not a partner yet". */
    private List<OnboardingOrganizationResponse> organizations;
    /** Unfinished drafts, most recently touched first. */
    private List<DraftSummaryResponse> drafts;
    private int maxActiveDrafts;
}

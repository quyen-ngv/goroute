package com.ds.goroute.partneronboarding.dto;

import com.ds.goroute.partneronboarding.domain.ListingKind;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A draft as the wizard needs it: the answers it already has, where it left off, and the
 * version to send back with the next write.
 */
@Data
@Builder
public class DraftResponse {

    private UUID id;
    private ListingKind listingKind;
    private String status;
    private UUID organizationId;
    /** Present once the organization step has run; lets the wizard show the verification banner. */
    private OnboardingOrganizationResponse organization;
    private String currentStep;
    private List<String> completedSteps;
    private Map<String, Object> data;
    /** Set on a submitted draft, so a client resuming a finished flow can open the listing. */
    private UUID resultHotelId;
    private UUID resultActivityId;
    private LocalDateTime submittedAt;
    /** Send this back as {@code expectedVersion} on the next write. */
    private Long expectedVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

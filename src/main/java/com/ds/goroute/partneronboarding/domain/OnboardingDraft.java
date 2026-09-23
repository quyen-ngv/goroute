package com.ds.goroute.partneronboarding.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One attempt at creating one listing.
 *
 * <p>{@code completedSteps} and {@code data} are stored as JSON text because the server does
 * not interpret the answers: it hands them back to the client that wrote them and, at
 * submit, to the materializer for this kind. Keeping them opaque is what lets a step be
 * added to the wizard without a migration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingDraft {

    private UUID id;
    private UUID userId;
    private UUID organizationId;
    private String listingKind;
    private String status;
    private String currentStep;
    /** JSON array of step codes. */
    private String completedSteps;
    /** JSON object keyed by step code. */
    private String data;
    private UUID resultHotelId;
    private UUID resultActivityId;
    private LocalDateTime submittedAt;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ListingKind kind() {
        return ListingKind.valueOf(listingKind);
    }

    public DraftStatus draftStatus() {
        return DraftStatus.valueOf(status);
    }
}

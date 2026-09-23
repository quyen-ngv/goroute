package com.ds.goroute.partneronboarding.dto;

import com.ds.goroute.partneronboarding.domain.ListingKind;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateDraftRequest {

    @NotNull
    private ListingKind listingKind;

    /**
     * The business this listing belongs to. Optional: a first-time partner has no
     * organization yet and creates one inside the wizard.
     */
    private UUID organizationId;
}

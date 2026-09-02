package com.ds.goroute.dto.request;

import com.ds.goroute.type.OrganizationVerificationDocumentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class DecideOrganizationVerificationRequest {
    @NotNull
    private Boolean approve;
    /** Required when rejecting; shown to the partner. */
    @Size(max = 2000)
    private String reason;
    @NotNull
    private Long expectedVersion;
    @Valid @Size(max = 100)
    private List<DocumentDecision> documentDecisions;

    @Data
    public static class DocumentDecision {
        @NotNull
        private UUID documentId;
        @NotNull
        private OrganizationVerificationDocumentStatus status;
        @Size(max = 1000)
        private String reviewNote;
    }
}

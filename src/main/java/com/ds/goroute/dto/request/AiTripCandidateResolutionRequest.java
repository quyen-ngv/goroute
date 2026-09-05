package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Internal worker request: resolve cited Web names without exposing any map-provider key. */
@Data
public class AiTripCandidateResolutionRequest {
    @NotNull private BigDecimal latitude;
    @NotNull private BigDecimal longitude;
    @NotNull @Valid private List<Candidate> candidates;

    @Data
    public static class Candidate {
        @NotBlank private String candidateId;
        @NotBlank private String title;
        @NotBlank private String placeGroup;
    }
}

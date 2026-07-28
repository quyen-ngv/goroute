package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NationwideDuplicateCheckRequest {
    @NotNull
    private UUID jobId;

    @NotBlank
    private String pythonJobId;

    @NotEmpty
    @Size(max = 500)
    private List<@Valid Candidate> candidates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        @NotBlank
        private String candidateKey;
        private String googlePlaceId;
        private String cid;
        private BigDecimal latitude;
        private BigDecimal longitude;
    }
}

package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripConfirmRequest {
    @NotNull(message = "Selected candidate ids are required")
    private List<String> selectedCandidateIds;

    @Size(max = 120)
    private String idempotencyKey;
}

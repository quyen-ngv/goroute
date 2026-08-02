package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NationwidePlaceImportRequest {
    @NotNull
    private UUID jobId;
    @NotBlank
    private String pythonJobId;
    @NotBlank
    private String regionCode;
    @NotBlank
    private String regionName;
    private String searchQuery;
    private String filterReason;
    @NotNull @Valid
    private ImportPlaceRequest place;
    @NotNull @Size(max = 200) @Valid
    private List<ReviewInput> reviews;
}

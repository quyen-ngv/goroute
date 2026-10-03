package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Scrape worker request: find a place by free text through the backend's map provider. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalPlaceSearchRequest {

    @NotBlank
    @Size(max = 300)
    private String query;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer limit;
}

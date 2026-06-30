package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckContributionRequest {

    @NotBlank(message = "Google Maps URL is required")
    private String googleMapsUrl;
}

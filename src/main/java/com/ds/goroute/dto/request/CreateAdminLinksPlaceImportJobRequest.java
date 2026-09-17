package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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
public class CreateAdminLinksPlaceImportJobRequest {

    /**
     * One job walks its links one at a time and each link costs a full browser scrape, so the
     * batch is capped to keep a single paste from occupying the scrape worker for hours.
     */
    @NotEmpty(message = "At least one Google Maps URL is required")
    @Size(max = 50, message = "At most 50 URLs per job")
    private List<@Pattern(regexp = "https?://.+", message = "Each URL must be an HTTP(S) URL") String> urls;

    @Pattern(regexp = "ACTIVE|INACTIVE", message = "visibilityStatus must be ACTIVE or INACTIVE")
    @Builder.Default
    private String visibilityStatus = "INACTIVE";
}

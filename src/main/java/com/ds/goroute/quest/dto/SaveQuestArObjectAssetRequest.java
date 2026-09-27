package com.ds.goroute.quest.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Creates or updates a library object (§3.15). The model files are uploaded first through
 * {@code /files}, which returns their URLs; the clips, triangle count and whether it can wander are
 * read from the GLB on the server at save, never taken from here.
 *
 * @param expectedVersion the {@code dataVersion} the console loaded; ignored on create.
 */
public record SaveQuestArObjectAssetRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotBlank String glbUrl,
        String usdzUrl,
        String thumbnailUrl,
        @DecimalMin("0.01") @DecimalMax("50") BigDecimal heightM,
        @Size(max = 20) List<@Size(max = 40) String> tags,
        Boolean active,
        Long expectedVersion) {
}

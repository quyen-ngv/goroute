package com.ds.goroute.quest.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A creator's own 3D object (§3.15), from files uploaded first through
 * {@code /quest-builder/ar-objects/files}. The URLs must be the ones that upload returned; clips,
 * triangles and whether it can wander are read from the stored GLB, never taken from here.
 */
public record SaveCreatorArObjectRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotBlank String glbUrl,
        String usdzUrl,
        String thumbnailUrl,
        @DecimalMin("0.01") @DecimalMax("50") BigDecimal heightM) {
}

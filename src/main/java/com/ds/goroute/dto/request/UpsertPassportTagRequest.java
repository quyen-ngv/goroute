package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Operator payload for a tag and the curated Place references it uses. */
@Data
public class UpsertPassportTagRequest {
    @NotNull
    private UUID passportId;

    @NotBlank
    @Size(max = 80)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 5000)
    private String description;

    @Size(max = 1000)
    private String imageUrl;

    /** Optional for backwards compatibility; omitted mode is inferred from placeIds. */
    @Pattern(regexp = "(?i)SPECIFIC_PLACES|PASSPORT_PROVINCES|PASSPORT_LOCATIONS")
    private String qualificationMode;

    @Min(1)
    @Max(1000)
    private Integer requiredCheckinCount = 1;

    @JsonProperty("isActive")
    @JsonAlias("active")
    private Boolean isActive = true;

    @Min(0)
    @Max(100000)
    private Integer displayOrder = 0;

    @Size(max = 100)
    private List<UUID> placeIds = List.of();
}

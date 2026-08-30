package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Operator payload for a curated passport collection. */
@Data
public class UpsertPassportDefinitionRequest {
    @NotBlank
    @Size(max = 80)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 5000)
    private String description;

    @Size(max = 1000)
    private String coverImageUrl;

    @JsonProperty("isActive")
    @JsonAlias("active")
    private Boolean isActive = true;

    @Min(0)
    @Max(100000)
    private Integer displayOrder = 0;

    /** New Passport scope: one or more curated Location Images. */
    @Size(max = 100)
    private List<UUID> locationImageIds = List.of();

    /** Legacy field accepted during rolling migration; new admin UI does not send it. */
    @Size(max = 200)
    private List<@Size(max = 10) String> provinceCodes = List.of();
}

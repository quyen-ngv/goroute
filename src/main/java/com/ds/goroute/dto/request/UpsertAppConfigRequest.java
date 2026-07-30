package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertAppConfigRequest {
    @NotBlank
    @Size(max = 100)
    private String label;

    @NotBlank
    @Size(max = 150)
    private String key;

    @NotBlank
    private String value;

    private String description;
    private Boolean isActive = true;
    private Long expectedVersion;
}

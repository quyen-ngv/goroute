package com.ds.goroute.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Operator payload for the Passport reward catalogue. */
@Data
public class UpsertPassportRewardRequest {
    @NotBlank
    @Size(max = 60)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 5000)
    private String description;

    @Min(0)
    @Max(1000000)
    private Integer pointsCost = 0;

    @Size(max = 60)
    private String requiredStampCode;

    @Min(0)
    @Max(100000000)
    private Integer totalQuantity;

    @Min(1)
    @Max(3650)
    private Integer validDays = 30;

    @JsonProperty("isActive")
    @JsonAlias("active")
    private Boolean isActive = true;
}

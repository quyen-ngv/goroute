package com.ds.goroute.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Operator payload for a versioned Passport milestone rule. */
@Data
public class UpsertPassportStampRuleRequest {
    @NotBlank
    @Size(max = 60)
    private String code;

    @Min(1)
    @Max(100000)
    private Integer version = 1;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 5000)
    private String description;

    @NotBlank
    @Pattern(regexp = "FIRST_CHECKIN|CHECKIN_COUNT|DISTINCT_PLACE_COUNT|DISTINCT_PROVINCE_COUNT|VERIFIED_CHECKIN_COUNT")
    private String conditionType;

    @Min(1)
    @Max(100000)
    private Integer threshold = 1;

    @Size(max = 100)
    private String icon;

    @Min(0)
    @Max(1000000)
    private Integer rewardPoints = 0;

    @JsonProperty("isActive")
    @JsonAlias("active")
    private Boolean isActive = true;
}

package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class UpsertRoomTypeRequest {
    @NotBlank @Size(max = 100) private String code;
    @NotBlank @Size(max = 500) private String name;
    private String description;
    @Min(1) private Integer maxAdults = 1;
    @Min(0) private Integer maxChildren = 0;
    @Min(1) private Integer maxOccupancy = 1;
    private List<Map<String, Object>> bedConfig;
    private List<String> amenities;
    private List<String> images;
    @DecimalMin("0") private BigDecimal roomSizeSqm;
    @Min(0) private Integer totalUnits = 1;
    @Pattern(regexp = "ENABLED|DISABLED|ARCHIVED") private String status = "ENABLED";
    private String disabledReason;
    private Long expectedVersion;
}

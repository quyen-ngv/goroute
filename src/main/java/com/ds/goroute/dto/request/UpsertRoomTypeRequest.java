package com.ds.goroute.dto.request;

import com.ds.goroute.type.MarketplaceAvailabilityStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotNull @Min(1) private Integer maxAdults = 1;
    @NotNull @Min(0) private Integer maxChildren = 0;
    @NotNull @Min(1) private Integer maxOccupancy = 1;
    private List<Map<String, Object>> bedConfig;
    private List<String> amenities;
    private List<String> images;
    @DecimalMin("0") private BigDecimal roomSizeSqm;
    @NotNull @Min(0) private Integer totalUnits = 1;
    private MarketplaceAvailabilityStatus status = MarketplaceAvailabilityStatus.ENABLED;
    private String disabledReason;
    private Long expectedVersion;
}

package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class UpsertMarketplaceActivityRequest {
    @NotNull private UUID organizationId;
    private UUID placeId;
    @NotBlank @Size(max=500) private String title;
    private String description;
    @Size(max=500) private String activityAddress;
    @DecimalMin("0") private BigDecimal priceAmount;
    @Pattern(regexp="[A-Z]{3}") private String priceCurrency="VND";
    @Size(max=100) private String durationRaw;
    @DecimalMin("0") private BigDecimal durationHours;
    private Integer visitDurationMinutes;
    private String thumbnail;
    private List<String> images;
    private List<String> highlights;
    @Pattern(regexp="DRAFT|ENABLED|DISABLED|ARCHIVED") private String productStatus="DRAFT";
    private Long expectedVersion;
}

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
import java.util.Map;

@Data
public class UpsertActivityPackageRequest {
    @NotBlank @Size(max=100) private String code;
    @NotBlank @Size(max=500) private String name;
    private String description;
    @Pattern(regexp="[A-Z]{3}") private String currency="VND";
    @NotNull @DecimalMin("0") private BigDecimal basePrice;
    @NotNull @Min(1) private Integer minQuantity=1;
    @Min(1) private Integer maxQuantity;
    private Map<String,Object> attributes;
    private Map<String,Object> cancellationPolicy;
    private MarketplaceAvailabilityStatus status=MarketplaceAvailabilityStatus.ENABLED;
    private Long expectedVersion;
}

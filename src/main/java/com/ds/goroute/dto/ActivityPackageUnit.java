package com.ds.goroute.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import com.ds.goroute.type.ActivityUnitType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityPackageUnit {
    @NotBlank @Size(max = 100) private String code;
    @NotBlank @Size(max = 200) private String name;
    @jakarta.validation.constraints.NotNull private ActivityUnitType unitType;
    @DecimalMin("0") private BigDecimal price;
    @Min(0) private Integer minAge;
    @Min(0) private Integer maxAge;
    @Min(0) private Integer minQuantity;
    @Min(1) private Integer maxQuantity;
    @Min(1) private Integer paxCount;
    private Boolean idRequired;
}

package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpsertActivitySlotRequest {
    @NotNull private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    @NotBlank private String timezone;
    @Min(0) private Integer capacity;
    @Min(0) private Integer blockedQuantity=0;
    @Min(0) private Integer bookingCutoffMinutes=0;
    @DecimalMin("0") private BigDecimal priceOverride;
    @Pattern(regexp="ENABLED|DISABLED|CANCELLED|COMPLETED") private String status="ENABLED";
    private Long expectedVersion;
}

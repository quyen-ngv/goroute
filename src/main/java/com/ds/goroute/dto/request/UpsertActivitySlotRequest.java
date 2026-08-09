package com.ds.goroute.dto.request;

import com.ds.goroute.type.MarketplaceSlotStatus;

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
    @NotNull @Min(0) private Integer capacity;
    @NotNull @Min(0) private Integer blockedQuantity=0;
    @NotNull @Min(0) private Integer bookingCutoffMinutes=0;
    @DecimalMin("0") private BigDecimal priceOverride;
    private MarketplaceSlotStatus status=MarketplaceSlotStatus.ENABLED;
    private Long expectedVersion;
}

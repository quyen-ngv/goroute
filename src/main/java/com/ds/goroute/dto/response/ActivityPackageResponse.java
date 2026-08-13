package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;
import com.ds.goroute.dto.ActivityPackageUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class ActivityPackageResponse {
    private UUID id; private UUID activityId; private String code; private String name; private String description;
    private String currency; private BigDecimal basePrice; private Integer minQuantity; private Integer maxQuantity;
    private String inventoryType; private List<ActivityPackageUnit> units; private List<String> includedItems; private List<String> excludedItems;
    private List<String> requiredInformation; private String confirmationType; private String voucherType; private Integer validityDays;
    private Map<String,Object> attributes; private Map<String,Object> cancellationPolicy; private String status;
    private Long dataVersion; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}

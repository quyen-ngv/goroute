package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateActivityOrderRequest {
    @NotNull private UUID activityId;
    @NotNull private UUID packageId;
    @NotNull private UUID slotId;
    @Min(1) private Integer quantity=1;
    private Map<String, @Min(0) Integer> unitQuantities;
    private List<Map<String,Object>> participants;
    private Map<String,Object> contactInfo;
    private String specialRequests;
    @Size(max = 120) private String idempotencyKey;
}

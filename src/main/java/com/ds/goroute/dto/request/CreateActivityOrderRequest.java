package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    private List<Map<String,Object>> participants;
}

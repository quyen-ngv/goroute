package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderActivitiesRequest {
    
    @NotEmpty(message = "Activities list cannot be empty")
    @Valid
    private List<ActivityOrderDto> activities;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityOrderDto {
        
        @NotNull(message = "Activity ID is required")
        private UUID id;
        
        @NotNull(message = "Sort order is required")
        @PositiveOrZero(message = "Sort order must be zero or positive")
        private Integer sortOrder;
    }
}

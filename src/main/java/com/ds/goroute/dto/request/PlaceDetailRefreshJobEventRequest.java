package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceDetailRefreshJobEventRequest {
    @NotNull
    private UUID jobId;
    @NotBlank
    private String pythonJobId;
    @NotBlank
    private String eventType;
    private Integer databaseCount;
    private Integer eligibleCount;
    private Integer processedCount;
    private Integer successCount;
    private Integer failedCount;
    private UUID currentPlaceId;
    private String currentTitle;
    private String errorMessage;
}

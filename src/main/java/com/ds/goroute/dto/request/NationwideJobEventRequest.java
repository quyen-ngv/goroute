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
public class NationwideJobEventRequest {
    @NotNull
    private UUID jobId;
    @NotBlank
    private String pythonJobId;
    @NotBlank
    private String eventType;
    private String regionCode;
    private String regionName;
    private Integer priority;
    private Integer sequenceNo;
    private String regionStatus;
    private Integer queryCount;
    private Integer discoveredCount;
    private Integer processedCount;
    private Integer eligibleCount;
    private Integer importedCount;
    private Integer skippedCount;
    private Integer failedCount;
    private Integer rejectedScoreCount;
    private Integer insufficientPhotoCount;
    private String errorMessage;
}

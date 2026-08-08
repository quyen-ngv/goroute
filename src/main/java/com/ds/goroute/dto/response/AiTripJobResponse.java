package com.ds.goroute.dto.response;

import com.ds.goroute.entity.AiTripGenerationJob;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
public class AiTripJobResponse {
    private UUID jobId;
    private String attemptId;
    private String status;
    private String stage;
    private int progress;
    private UUID tripId;
    private String errorMessage;

    public static AiTripJobResponse from(AiTripGenerationJob job) {
        return builder().jobId(job.getId()).attemptId(job.getAttemptId()).status(job.getStatus())
                .stage(job.getStage()).progress(job.getProgress()).tripId(job.getCreatedTripId())
                .errorMessage(job.getErrorMessage()).build();
    }
}

package com.ds.goroute.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Map;

@Data
public class AiTripJobEventRequest {
    @NotBlank private String attemptId;
    @NotBlank private String stage;
    @NotBlank private String status;
    @Min(0) @Max(100) private int progress;
    private String messageKey;
    private Map<String,Object> params;
    private String errorMessage;
}

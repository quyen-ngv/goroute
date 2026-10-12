package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** The worker could not answer; the asker is told and the question is not counted. */
@Data
public class AssistantFailRequest {
    @Size(max = 1000)
    private String error;
}

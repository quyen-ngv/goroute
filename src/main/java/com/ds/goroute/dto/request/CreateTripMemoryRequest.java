package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateTripMemoryRequest {
    @NotBlank
    private String url;
    private UUID activityId;
    private String caption;
}

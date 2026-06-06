package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCityStoryRequest {
    @NotBlank
    private String imageUrl;
    private String description;
    private UUID placeId;
}

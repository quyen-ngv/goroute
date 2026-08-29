package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateTripMemoryRequest {
    @NotBlank
    private String url;
    private UUID activityId;
    @ModeratedText(contentType = ModeratedContentType.TRIP_MEMORY, visibility = ModerationVisibility.GROUP)
    private String caption;
}

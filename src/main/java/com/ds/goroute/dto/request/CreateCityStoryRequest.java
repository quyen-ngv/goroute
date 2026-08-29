package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCityStoryRequest {
    @NotBlank
    private String imageUrl;
    @ModeratedText(contentType = ModeratedContentType.CITY_STORY, visibility = ModerationVisibility.PUBLIC)
    private String description;
    private UUID placeId;
}

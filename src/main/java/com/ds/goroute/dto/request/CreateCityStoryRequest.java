package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCityStoryRequest {
    /** Required when {@code mediaType} is {@code IMAGE} (the default); unused for video. */
    private String imageUrl;
    /** {@code IMAGE} or {@code VIDEO}; defaults to {@code IMAGE} when omitted. */
    @Pattern(regexp = "IMAGE|VIDEO", message = "mediaType must be IMAGE or VIDEO")
    private String mediaType;
    /** Required when {@code mediaType} is {@code VIDEO}; unused for images. */
    private String videoUrl;
    /** Poster frame for a video story; ignored for images. */
    private String thumbnailUrl;
    @ModeratedText(contentType = ModeratedContentType.CITY_STORY, visibility = ModerationVisibility.PUBLIC)
    private String description;
    private UUID placeId;
}

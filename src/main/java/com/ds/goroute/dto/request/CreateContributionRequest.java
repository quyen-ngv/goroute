package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContributionRequest {

    @NotBlank(message = "Google Maps URL is required")
    private String googleMapsUrl;

    private String placeNameHint;

    @NotNull(message = "Overall rating is required")
    @Min(1) @Max(5)
    private Integer overallRating;

    @Min(1) @Max(5)
    private Integer foodRating;

    @Min(1) @Max(5)
    private Integer priceRating;

    @Min(1) @Max(5)
    private Integer ambianceRating;

    @Min(1) @Max(5)
    private Integer serviceRating;

    @Size(max = 2000)
    @ModeratedText(contentType = ModeratedContentType.USER_PLACE, visibility = ModerationVisibility.PUBLIC)
    private String text;

    private List<String> photos;
}

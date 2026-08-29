package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpsertGuideProfileRequest {

    @NotBlank(message = "A display name is required")
    @Size(max = 200)
    @ModeratedText(contentType = ModeratedContentType.GUIDE_PROFILE, visibility = ModerationVisibility.PUBLIC)
    private String displayName;

    @Size(max = 300)
    @ModeratedText(contentType = ModeratedContentType.GUIDE_PROFILE, visibility = ModerationVisibility.PUBLIC)
    private String headline;

    @Size(max = 5000)
    @ModeratedText(contentType = ModeratedContentType.GUIDE_PROFILE, visibility = ModerationVisibility.PUBLIC)
    private String bio;

    private List<String> languages;

    private List<String> areaProvinceCodes;

    @Min(0) @Max(80)
    private Integer yearsExperience;

    @Size(max = 1000)
    private String avatarUrl;

    /** Kept internal; never part of the public profile. */
    @Size(max = 40)
    private String contactPhone;

    @Email
    @Size(max = 200)
    private String contactEmail;
}

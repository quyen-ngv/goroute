package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @Size(max = 255)
    @ModeratedText(contentType = ModeratedContentType.USER_PROFILE, visibility = ModerationVisibility.PUBLIC)
    private String fullName;

    @Size(max = 50)
    private String username;

    @Size(max = 280)
    @ModeratedText(contentType = ModeratedContentType.USER_PROFILE, visibility = ModerationVisibility.PUBLIC)
    private String bio;

    private Map<String, String> socialLinks;

    private Boolean completeOnboarding;
}

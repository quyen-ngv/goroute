package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Creating or editing a managed account (POST and PUT /v1/api/admin/managed-accounts). The name and
 * bio are public profile text, so they go through the same filter as a user's own.
 *
 * <p>{@code guideTitle}: on create, null means the default "Local Guide" badge and an empty string
 * no badge; on update, null leaves the badge as it is and an empty string revokes it.
 */
@Data
public class ManagedAccountRequest {

    @NotBlank
    @Size(max = 100)
    @ModeratedText(contentType = ModeratedContentType.USER_PROFILE, visibility = ModerationVisibility.PUBLIC)
    private String fullName;

    /** Same rule as the partner sign-up handle; also the local part of the placeholder email. */
    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Username may only contain letters, digits, '.', '_' and '-'")
    private String username;

    @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.USER_PROFILE, visibility = ModerationVisibility.PUBLIC)
    private String bio;

    @Size(max = 1000)
    private String avatarUrl;

    @Size(max = 100)
    private String guideTitle;
}

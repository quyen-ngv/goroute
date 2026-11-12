package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.PostType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/** A new draft written by the console for a managed account (POST /v1/api/admin/posts). */
@Data
public class AdminCreatePostRequest {

    /** Must be users.managed_by_admin, or the answer is ACCOUNT_NOT_MANAGED (409). */
    @NotNull
    private UUID authorUserId;

    @NotNull
    private PostType postType;

    @NotBlank
    @Size(max = 200)
    @ModeratedText(contentType = ModeratedContentType.POST, visibility = ModerationVisibility.PUBLIC)
    private String title;

    private UUID tripId;
}

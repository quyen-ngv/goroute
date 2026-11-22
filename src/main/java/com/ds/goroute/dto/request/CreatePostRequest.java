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

/** A new draft (POST /v1/api/posts). The body, places and cover come with the first save. */
@Data
public class CreatePostRequest {

    @NotNull
    private PostType postType;

    @NotBlank
    @Size(max = 200)
    @ModeratedText(contentType = ModeratedContentType.POST, visibility = ModerationVisibility.PUBLIC)
    private String title;

    /** TRIP_STORY only: a trip the author is an accepted member of. */
    private UUID tripId;
}

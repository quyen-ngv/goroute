package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Request payload for comments on public posts and a trip's main thread. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContentCommentRequest {
    @NotNull(message = "Content type is required")
    private ModeratedContentType contentType;

    @NotNull(message = "Content id is required")
    private UUID contentId;

    /** Null means a top-level comment; otherwise this is a reply at any depth. */
    private UUID parentId;

    @NotBlank(message = "Content is required")
    @Size(max = 1000, message = "Content must not exceed 1000 characters")
    @ModeratedText(contentType = ModeratedContentType.CONTENT_COMMENT,
            visibility = ModerationVisibility.PUBLIC)
    private String content;
}

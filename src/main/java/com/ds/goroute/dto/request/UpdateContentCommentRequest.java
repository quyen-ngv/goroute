package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateContentCommentRequest {
    @NotBlank(message = "Content is required")
    @Size(max = 1000, message = "Content must not exceed 1000 characters")
    @ModeratedText(contentType = ModeratedContentType.CONTENT_COMMENT,
            visibility = ModerationVisibility.PUBLIC)
    private String content;
}

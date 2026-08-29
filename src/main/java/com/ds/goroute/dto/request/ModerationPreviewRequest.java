package com.ds.goroute.dto.request;

import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Tries a passage against the current list without saving anything.
 *
 * <p>For operators this is the only way to find out whether a new entry causes false
 * blocks before it reaches real users, which matters more than how the screen looks.
 */
@Data
public class ModerationPreviewRequest {

    @NotBlank(message = "Text is required")
    @Size(max = 5000, message = "Text cannot exceed 5000 characters")
    private String text;

    private ModerationVisibility visibility;
}

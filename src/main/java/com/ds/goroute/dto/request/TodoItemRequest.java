package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** A new item of a todo-list note (epic 04 §2.4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoItemRequest {

    @NotBlank(message = "Text is required")
    @Size(max = 500, message = "Text must not exceed 500 characters")
    @ModeratedText(contentType = ModeratedContentType.TRIP_NOTE, visibility = ModerationVisibility.GROUP)
    private String text;

    /** Trip members to assign; may be empty. */
    private List<UUID> assigneeIds;
}

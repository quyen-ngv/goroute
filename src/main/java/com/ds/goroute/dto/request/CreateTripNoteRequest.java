package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTripNoteRequest {

    private String activityId;  // Optional: for activity-specific notes

    @NotBlank(message = "Content is required")
    @Size(max = 2000, message = "Content must not exceed 2000 characters")
    @ModeratedText(contentType = ModeratedContentType.TRIP_NOTE, visibility = ModerationVisibility.GROUP)
    private String content;

    private Boolean isShared;

    /** {@code TEXT} (default) or {@code TODO}; for a todo list {@link #content} is its title. */
    private String noteType;

    /** First items of a todo list; ignored for a text note. */
    @Valid
    @Size(max = 200, message = "A todo list holds at most 200 items")
    private List<TodoItemRequest> todoItems;
}

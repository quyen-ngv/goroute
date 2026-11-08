package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** A change to one item of a todo-list note. A null field is left as it is. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTodoItemRequest {

    @Size(max = 500, message = "Text must not exceed 500 characters")
    @ModeratedText(contentType = ModeratedContentType.TRIP_NOTE, visibility = ModerationVisibility.GROUP)
    private String text;

    private Boolean isDone;

    /** The full new set of assignees; an empty list unassigns everyone. */
    private List<UUID> assigneeIds;
}

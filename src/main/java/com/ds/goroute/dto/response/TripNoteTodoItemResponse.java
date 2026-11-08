package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripNoteTodoItemResponse {
    private UUID id;
    private String text;
    private Integer sortOrder;
    private Boolean isDone;
    /** Who ticked it; null while not done. */
    private UserResponse doneBy;
    private LocalDateTime doneAt;
    private List<UserResponse> assignees;
    private LocalDateTime createdAt;
}

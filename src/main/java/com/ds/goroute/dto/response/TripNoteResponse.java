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
public class TripNoteResponse {
    private UUID id;
    private UUID tripId;
    private UUID activityId;  // Optional: for activity-specific notes
    private UserResponse user;
    /** {@code TEXT} or {@code TODO}; for a todo list {@link #content} is its title. */
    private String noteType;
    private String content;
    /** Items of a todo list, in list order; empty for a text note. */
    private List<TripNoteTodoItemResponse> todoItems;
    private Boolean isShared;
    private LocalDateTime createdAt;
}

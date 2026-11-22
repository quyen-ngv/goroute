package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One line of a todo-list note ({@code trip_note_todo_items}, V271). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripNoteTodoItem {
    private UUID id;
    private UUID noteId;
    private UUID tripId;
    private String text;
    private Integer sortOrder;
    private Boolean isDone;
    private UUID doneBy;
    private LocalDateTime doneAt;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

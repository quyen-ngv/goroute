package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A person an item of a todo-list note is assigned to ({@code trip_note_todo_assignees}, V271). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripNoteTodoAssignee {
    private UUID itemId;
    private UUID userId;
}

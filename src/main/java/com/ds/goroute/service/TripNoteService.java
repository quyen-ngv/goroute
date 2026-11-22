package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateTripNoteRequest;
import com.ds.goroute.dto.request.TodoItemRequest;
import com.ds.goroute.dto.request.UpdateTodoItemRequest;
import com.ds.goroute.dto.request.UpdateTripNoteRequest;
import com.ds.goroute.dto.response.TripNoteResponse;

import java.util.List;
import java.util.UUID;

public interface TripNoteService {
    List<TripNoteResponse> getTripNotes(UUID tripId, UUID userId);
    List<TripNoteResponse> getActivityNotes(UUID tripId, UUID activityId, UUID userId);
    TripNoteResponse createTripNote(UUID tripId, CreateTripNoteRequest request, UUID userId);
    TripNoteResponse updateTripNote(UUID tripId, UUID noteId, UpdateTripNoteRequest request, UUID userId);
    void deleteTripNote(UUID tripId, UUID noteId, UUID userId);

    /** Adds an item to a todo-list note; any accepted member may. Returns the whole note. */
    TripNoteResponse addTodoItem(UUID tripId, UUID noteId, TodoItemRequest request, UUID userId);

    /** Edits, ticks or reassigns an item; any accepted member may. Returns the whole note. */
    TripNoteResponse updateTodoItem(UUID tripId, UUID noteId, UUID itemId, UpdateTodoItemRequest request, UUID userId);

    /** Removes an item; any accepted member may. Returns the whole note. */
    TripNoteResponse deleteTodoItem(UUID tripId, UUID noteId, UUID itemId, UUID userId);
}

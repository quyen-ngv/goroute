package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateTripNoteRequest;
import com.ds.goroute.dto.request.TodoItemRequest;
import com.ds.goroute.dto.request.UpdateTodoItemRequest;
import com.ds.goroute.dto.request.UpdateTripNoteRequest;
import com.ds.goroute.dto.response.TripNoteResponse;
import com.ds.goroute.service.TripNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/trips/{tripId}/notes")
@RequiredArgsConstructor
@Slf4j
public class TripNoteController extends BaseController {
    
    private final TripNoteService tripNoteService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<TripNoteResponse>>> getTripNotes(
            @PathVariable UUID tripId,
            @RequestParam(required = false) UUID activityId,
            @CurrentUser UUID userId) {
        List<TripNoteResponse> notes;
        if (activityId != null) {
            notes = tripNoteService.getActivityNotes(tripId, activityId, userId);
        } else {
            notes = tripNoteService.getTripNotes(tripId, userId);
        }
        return ResponseEntity.ok(ofSucceeded(notes));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<TripNoteResponse>> createTripNote(
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateTripNoteRequest request,
            @CurrentUser UUID userId) {
        TripNoteResponse note = tripNoteService.createTripNote(tripId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(note));
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<BaseResponse<TripNoteResponse>> updateTripNote(
            @PathVariable UUID tripId,
            @PathVariable UUID noteId,
            @Valid @RequestBody UpdateTripNoteRequest request,
            @CurrentUser UUID userId) {
        TripNoteResponse note = tripNoteService.updateTripNote(tripId, noteId, request, userId);
        return ResponseEntity.ok(ofSucceeded(note));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<BaseResponse<Void>> deleteTripNote(
            @PathVariable UUID tripId,
            @PathVariable UUID noteId,
            @CurrentUser UUID userId) {
        tripNoteService.deleteTripNote(tripId, noteId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    // Todo-list items (epic 04 §2.4). Any accepted member may change them; each call
    // returns the whole note so the client replaces it in one go.

    @PostMapping("/{noteId}/todo-items")
    public ResponseEntity<BaseResponse<TripNoteResponse>> addTodoItem(
            @PathVariable UUID tripId,
            @PathVariable UUID noteId,
            @Valid @RequestBody TodoItemRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(tripNoteService.addTodoItem(tripId, noteId, request, userId)));
    }

    @PatchMapping("/{noteId}/todo-items/{itemId}")
    public ResponseEntity<BaseResponse<TripNoteResponse>> updateTodoItem(
            @PathVariable UUID tripId,
            @PathVariable UUID noteId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateTodoItemRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(tripNoteService.updateTodoItem(tripId, noteId, itemId, request, userId)));
    }

    @DeleteMapping("/{noteId}/todo-items/{itemId}")
    public ResponseEntity<BaseResponse<TripNoteResponse>> deleteTodoItem(
            @PathVariable UUID tripId,
            @PathVariable UUID noteId,
            @PathVariable UUID itemId,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(tripNoteService.deleteTodoItem(tripId, noteId, itemId, userId)));
    }
}

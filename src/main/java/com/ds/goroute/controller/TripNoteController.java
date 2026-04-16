package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateTripNoteRequest;
import com.ds.goroute.dto.response.TripNoteResponse;
import com.ds.goroute.service.BaseService;
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
public class TripNoteController extends BaseService {
    
    private final TripNoteService tripNoteService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<TripNoteResponse>>> getTripNotes(
            @PathVariable UUID tripId,
            @RequestAttribute("userId") UUID userId) {
        List<TripNoteResponse> notes = tripNoteService.getTripNotes(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(notes));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<TripNoteResponse>> createTripNote(
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateTripNoteRequest request,
            @RequestAttribute("userId") UUID userId) {
        TripNoteResponse note = tripNoteService.createTripNote(tripId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(note));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<BaseResponse<Void>> deleteTripNote(
            @PathVariable UUID tripId,
            @PathVariable UUID noteId,
            @RequestAttribute("userId") UUID userId) {
        tripNoteService.deleteTripNote(tripId, noteId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}

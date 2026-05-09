package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateTripNoteRequest;
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
}

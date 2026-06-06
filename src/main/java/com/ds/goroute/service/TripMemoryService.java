package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateTripMemoryRequest;
import com.ds.goroute.dto.response.TripMemoryResponse;

import java.util.List;
import java.util.UUID;

public interface TripMemoryService {
    List<TripMemoryResponse> getTripMemories(UUID tripId, UUID userId, UUID activityId);
    TripMemoryResponse addTripMemory(UUID tripId, CreateTripMemoryRequest request, UUID userId);
    void deleteTripMemory(UUID tripId, UUID memoryId, UUID userId);
}

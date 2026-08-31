package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateTripMemoryRequest;
import com.ds.goroute.dto.request.UpdateTripMemoryRequest;
import com.ds.goroute.dto.response.TripMemoryResponse;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface TripMemoryService {
    List<TripMemoryResponse> getTripMemories(UUID tripId, UUID userId, UUID activityId);
    TripMemoryResponse addTripMemory(UUID tripId, CreateTripMemoryRequest request, UUID userId);

    TripMemoryResponse addTripVideoMemory(UUID tripId, UUID activityId, MultipartFile file, UUID userId);
    TripMemoryResponse updateTripMemory(UUID tripId, UUID memoryId, UpdateTripMemoryRequest request, UUID userId);
    void deleteTripMemory(UUID tripId, UUID memoryId, UUID userId);
}

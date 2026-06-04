package com.ds.goroute.service;

import com.ds.goroute.dto.request.ImportActivityBookingRequest;
import com.ds.goroute.dto.request.UpdateActivityBookingRequest;
import com.ds.goroute.dto.response.ActivityBookingResponse;

import com.ds.goroute.dto.request.AddBookingToTripRequest;
import com.ds.goroute.dto.response.ActivityResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ActivityBookingService {

    ActivityBookingResponse importFromKlook(ImportActivityBookingRequest request);

    List<ActivityBookingResponse> search(
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minRating,
        List<String> destinations,
        BigDecimal latitude,
        BigDecimal longitude,
        Double radiusKm,
        String targetCurrency,
        int page,
        int size
    );

    List<ActivityBookingResponse> searchByPlace(
        UUID placeId,
        String query,
        Double radiusKm,
        String targetCurrency,
        int page,
        int size
    );

    ActivityBookingResponse getById(UUID id, String targetCurrency);

    ActivityBookingResponse updateById(UUID id, UpdateActivityBookingRequest request);

    void deleteById(UUID id);

    ActivityResponse addToTrip(UUID bookingId, AddBookingToTripRequest request, String targetCurrency, UUID userId);

    void triggerReindex();
}

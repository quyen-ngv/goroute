package com.ds.goroute.utils;

import com.ds.goroute.dto.GeoCoordinateDto;
import com.ds.goroute.entity.ActivityBooking;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Keeps denormalized search_lat/search_lng/destinations_norm in sync with JSON columns.
 */
public final class ActivityBookingSearchFieldHelper {

    private ActivityBookingSearchFieldHelper() {
    }

    public static void apply(ActivityBooking booking) {
        if (booking == null) {
            return;
        }
        GeoCoordinateDto primary = extractPrimaryCoordinate(booking.getDestinationCoordinates());
        booking.setSearchLat(primary != null && primary.getLat() != null
                ? primary.getLat().doubleValue() : null);
        booking.setSearchLng(primary != null && primary.getLng() != null
                ? primary.getLng().doubleValue() : null);
        booking.setDestinationsNorm(buildDestinationsNorm(booking.getDestinations()));
    }

    private static GeoCoordinateDto extractPrimaryCoordinate(String destinationCoordinatesJson) {
        List<GeoCoordinateDto> coordinates = JsonUtils.fromJson(
                destinationCoordinatesJson, new TypeReference<List<GeoCoordinateDto>>() {});
        if (coordinates == null || coordinates.isEmpty()) {
            return null;
        }
        return coordinates.get(0);
    }

    private static String buildDestinationsNorm(String destinationsJson) {
        List<String> destinations = JsonUtils.fromJson(
                destinationsJson, new TypeReference<List<String>>() {});
        if (destinations == null || destinations.isEmpty()) {
            return null;
        }
        String norm = destinations.stream()
                .map(DestinationMatchUtils::normalizeKey)
                .filter(key -> !key.isEmpty())
                .distinct()
                .collect(Collectors.joining("|"));
        return norm.isEmpty() ? null : norm;
    }
}

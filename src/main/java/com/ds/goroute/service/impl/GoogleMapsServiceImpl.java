package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.GoogleMapsService;
import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.DirectionsApi;
import com.google.maps.DistanceMatrixApi;
import com.google.maps.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleMapsServiceImpl implements GoogleMapsService {

    @Value("${google.maps.api-key}")
    private String apiKey;

    private GeoApiContext getContext() {
        return new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();
    }

    @Override
    // @Cacheable(value = "search", key = "#query + '_' + #lat + '_' + #lng")
    public List<PlaceSearchResponse> searchPlaces(String query, Double lat, Double lng) {
        try {
            GeoApiContext context = getContext();
            LatLng location = lat != null && lng != null ? new LatLng(lat, lng) : null;
            
            AutocompletePrediction[] predictions = PlacesApi.placeAutocomplete(context, query, null)
                    .location(location)
                    .radius(50000) // 50km
                    .await();

            return Arrays.stream(predictions)
                    .map(p -> PlaceSearchResponse.builder()
                            .placeId(p.placeId)
                            .name(p.structuredFormatting.mainText)
                            .address(p.description)
                            .description(p.structuredFormatting.secondaryText)
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error searching places: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    // @Cacheable(value = "place", key = "#placeId")
    public PlaceDetailResponse getPlaceDetails(String placeId) {
//        try {
//            GeoApiContext context = getContext();
//            PlaceDetails details = PlacesApi.placeDetails(context, placeId).await();
//
//            PlaceDetailResponse.PlaceDetailResponseBuilder builder = PlaceDetailResponse.builder()
//                    .placeId(placeId)
//                    .name(details.name)
//                    .address(details.formattedAddress)
//                    .lat(details.geometry.location.lat)
//                    .lng(details.geometry.location.lng)
//                    .rating((double) details.rating)
//                    .totalRatings(details.userRatingsTotal)
//                    .priceLevel(details.priceLevel != null ? details.priceLevel.ordinal() : null)
//                    .phoneNumber(details.formattedPhoneNumber)
//                    .website(details.website != null ? details.website.toString() : null);
//
//            if (details.types != null) {
//                builder.types(Arrays.stream(details.types).collect(Collectors.toList()));
//            }
//
//            if (details.openingHours != null) {
//                PlaceDetailResponse.OpeningHours openingHours = PlaceDetailResponse.OpeningHours.builder()
//                        .openNow(details.openingHours.openNow)
//                        .weekdayText(details.openingHours.weekdayText != null ?
//                                Arrays.asList(details.openingHours.weekdayText) : null)
//                        .build();
//                builder.openingHours(openingHours);
//            }
//
//            if (details.photos != null && details.photos.length > 0) {
//                List<PlaceDetailResponse.Photo> photos = Arrays.stream(details.photos)
//                        .map(p -> PlaceDetailResponse.Photo.builder()
//                                .photoReference(p.photoReference)
//                                .width(p.width)
//                                .height(p.height)
//                                .url("https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference="
//                                        + p.photoReference + "&key=" + apiKey)
//                                .build())
//                        .collect(Collectors.toList());
//                builder.photos(photos);
//            }
//
//            return builder.build();
//        } catch (Exception e) {
//            log.error("Error getting place details for {}: {}", placeId, e.getMessage(), e);
//            throw new RuntimeException("Failed to get place details", e);
//        }
        return null;
    }

    @Override
    // @Cacheable(value = "nearby", key = "#lat + '_' + #lng + '_' + #radius + '_' + #type")
    public List<NearbyPlaceResponse> nearbySearch(Double lat, Double lng, Integer radius, String type) {
//        try {
//            GeoApiContext context = getContext();
//            LatLng location = new LatLng(lat, lng);
//
//            PlacesSearchResponse response = PlacesApi.nearbySearchQuery(context, location, radius)
//                    .type(PlaceType.valueOf(type.toUpperCase()))
//                    .await();
//
//            return Arrays.stream(response.results)
//                    .map(p -> {
//                        String photoUrl = null;
//                        if (p.photos != null && p.photos.length > 0) {
//                            photoUrl = "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photoreference="
//                                    + p.photos[0].photoReference + "&key=" + apiKey;
//                        }
//
//                        return NearbyPlaceResponse.builder()
//                                .placeId(p.placeId)
//                                .name(p.name)
//                                .address(p.vicinity)
//                                .lat(p.geometry.location.lat)
//                                .lng(p.geometry.location.lng)
//                                .rating(p.rating != null ? p.rating.doubleValue() : null)
//                                .totalRatings(p.userRatingsTotal)
//                                .category(p.types != null && p.types.length > 0 ? p.types[0] : null)
//                                .photoUrl(photoUrl)
//                                .openNow(p.openingHours != null ? p.openingHours.openNow : null)
//                                .priceLevel(p.priceLevel != null ? p.priceLevel.ordinal() : null)
//                                .build();
//                    })
//                    .collect(Collectors.toList());
//        } catch (Exception e) {
//            log.error("Error nearby search: {}", e.getMessage(), e);
//            return new ArrayList<>();
//        }
        return null;
    }

    @Override
    // @Cacheable(value = "dir", key = "#originLat + '_' + #originLng + '_' + #destLat + '_' + #destLng + '_' + #travelMode")
    public RouteResponse calculateRoute(Double originLat, Double originLng, 
                                       Double destLat, Double destLng, 
                                       String travelMode) {
        try {
            GeoApiContext context = getContext();
            LatLng origin = new LatLng(originLat, originLng);
            LatLng destination = new LatLng(destLat, destLng);
            
            TravelMode mode = TravelMode.valueOf(travelMode.toUpperCase());
            
            DirectionsResult result = DirectionsApi.newRequest(context)
                    .origin(origin)
                    .destination(destination)
                    .mode(mode)
                    .await();

            if (result.routes.length == 0) {
                return null;
            }

            DirectionsRoute route = result.routes[0];
            DirectionsLeg leg = route.legs[0];

            List<RouteResponse.Step> steps = Arrays.stream(leg.steps)
                    .map(s -> RouteResponse.Step.builder()
                            .instruction(s.htmlInstructions)
                            .distanceKm(s.distance.inMeters / 1000.0)
                            .durationMinutes((int) (s.duration.inSeconds / 60))
                            .travelMode(s.travelMode.toString())
                            .build())
                    .collect(Collectors.toList());

            return RouteResponse.builder()
                    .distanceKm(leg.distance.inMeters / 1000.0)
                    .durationMinutes((int) (leg.duration.inSeconds / 60))
                    .polyline(route.overviewPolyline.getEncodedPath())
                    .steps(steps)
                    .build();
        } catch (Exception e) {
            log.error("Error calculating route: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    // @Cacheable(value = "dm", key = "#origins.toString() + '_' + #destinations.toString() + '_' + #travelMode")
    public DistanceMatrixResponse calculateDistanceMatrix(List<String> origins, 
                                                          List<String> destinations, 
                                                          String travelMode) {
        try {
            GeoApiContext context = getContext();
            TravelMode mode = TravelMode.valueOf(travelMode.toUpperCase());
            
            DistanceMatrix matrix = DistanceMatrixApi.newRequest(context)
                    .origins(origins.toArray(new String[0]))
                    .destinations(destinations.toArray(new String[0]))
                    .mode(mode)
                    .await();

            List<List<DistanceMatrixResponse.Element>> rows = Arrays.stream(matrix.rows)
                    .map(row -> Arrays.stream(row.elements)
                            .map(e -> DistanceMatrixResponse.Element.builder()
                                    .distanceKm(e.distance != null ? e.distance.inMeters / 1000.0 : null)
                                    .durationMinutes(e.duration != null ? (int) (e.duration.inSeconds / 60) : null)
                                    .status(e.status.toString())
                                    .build())
                            .collect(Collectors.toList()))
                    .collect(Collectors.toList());

            return DistanceMatrixResponse.builder()
                    .origins(origins)
                    .destinations(destinations)
                    .rows(rows)
                    .build();
        } catch (Exception e) {
            log.error("Error calculating distance matrix: {}", e.getMessage(), e);
            return null;
        }
    }
}

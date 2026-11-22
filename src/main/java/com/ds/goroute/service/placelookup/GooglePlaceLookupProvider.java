package com.ds.goroute.service.placelookup;

import com.ds.goroute.thirdparty.google.places.GooglePlace;
import com.ds.goroute.thirdparty.google.places.GooglePlacesClient;
import com.ds.goroute.thirdparty.google.places.GooglePlacesException;
import com.ds.goroute.thirdparty.google.places.GooglePlacesTextSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Google Places API (New) as a {@link PlaceLookupProvider}. Hits carry real Google place IDs. */
@Component
@RequiredArgsConstructor
public class GooglePlaceLookupProvider implements PlaceLookupProvider {

    /** Google's page size ceiling for Text Search. */
    private static final int MAX_PAGE_SIZE = 20;
    /** Google's ceiling for a circular location bias. */
    private static final int MAX_BIAS_RADIUS_METERS = 50_000;

    private final GooglePlacesClient client;

    @Override
    public PlaceLookupProviderType type() {
        return PlaceLookupProviderType.GOOGLE;
    }

    @Override
    public boolean isAvailable() {
        return client.isConfigured();
    }

    @Override
    public List<PlaceHit> searchText(PlaceLookupQuery query) {
        GooglePlacesTextSearchRequest request = GooglePlacesTextSearchRequest.builder()
                .textQuery(query.text())
                .pageSize(Math.min(query.limit(), MAX_PAGE_SIZE))
                .locationBias(query.hasBias() ? locationBias(query) : null)
                .build();
        try {
            return client.searchText(request).stream().map(this::toHit).toList();
        } catch (GooglePlacesException e) {
            throw new PlaceLookupException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<PlaceHit> getDetails(String providerPlaceId) {
        try {
            return client.getPlace(providerPlaceId).map(this::toHit);
        } catch (GooglePlacesException e) {
            throw new PlaceLookupException(e.getMessage(), e);
        }
    }

    private GooglePlacesTextSearchRequest.LocationBias locationBias(PlaceLookupQuery query) {
        int radius = query.biasRadiusMeters() == null
                ? MAX_BIAS_RADIUS_METERS
                : Math.min(query.biasRadiusMeters(), MAX_BIAS_RADIUS_METERS);
        return new GooglePlacesTextSearchRequest.LocationBias(new GooglePlacesTextSearchRequest.Circle(
                new GooglePlacesTextSearchRequest.Center(query.biasLatitude(), query.biasLongitude()),
                (double) radius));
    }

    private PlaceHit toHit(GooglePlace place) {
        String primaryType = place.getPrimaryType();
        if ((primaryType == null || primaryType.isBlank()) && place.getTypes() != null && !place.getTypes().isEmpty()) {
            primaryType = place.getTypes().get(0);
        }
        return new PlaceHit(
                type(),
                place.getId(),
                place.getDisplayName() == null ? null : place.getDisplayName().getText(),
                place.getFormattedAddress(),
                place.getLocation() == null ? null : place.getLocation().getLatitude(),
                place.getLocation() == null ? null : place.getLocation().getLongitude(),
                primaryType,
                place.getGoogleMapsUri());
    }
}

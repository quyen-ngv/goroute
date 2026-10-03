package com.ds.goroute.service.placelookup;

import java.math.BigDecimal;

/**
 * One place returned by a {@link PlaceLookupProvider}.
 *
 * <p>{@code providerPlaceId} belongs to {@code provider}: only a {@link PlaceLookupProviderType#GOOGLE}
 * hit carries a Google place ID. Any field may be null when the provider did not return it;
 * search-only answers (Goong autocomplete) have no coordinates.
 */
public record PlaceHit(
        PlaceLookupProviderType provider,
        String providerPlaceId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String primaryType,
        String googleMapsUri) {
}

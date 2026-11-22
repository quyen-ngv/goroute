package com.ds.goroute.service.placelookup;

import java.util.List;
import java.util.Optional;

/**
 * An external map service that finds a place by name and returns its details.
 *
 * <p>Only the name-to-place lookup is shared here. Provider-specific features (the app's Goong
 * proxy, reverse geocoding, distance matrices) stay on their own clients.
 */
public interface PlaceLookupProvider {

    PlaceLookupProviderType type();

    /** False when the provider has no credentials; every lookup would then fail. */
    boolean isAvailable();

    /**
     * Places matching the query, in the provider's ranking order.
     *
     * @throws PlaceLookupException when the provider is unavailable or did not answer usefully
     */
    List<PlaceHit> searchText(PlaceLookupQuery query);

    /**
     * Details of one place by this provider's own ID.
     *
     * @return empty when the provider answered but has no such place
     * @throws PlaceLookupException when the provider is unavailable or its answer is unreadable
     */
    Optional<PlaceHit> getDetails(String providerPlaceId);
}

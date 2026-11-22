package com.ds.goroute.service.placelookup;

import com.ds.goroute.service.mapsearch.MapProviderResolver;
import com.ds.goroute.type.MapProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Which map service looks a place up by name near a point, for every server-side lookup that
 * acts for one user (the AI trip planner's Web candidates, Pengoo's map search): Mapbox outside
 * Vietnam (Goong has nothing there), otherwise the user's own map provider (beta users and everyone
 * else are configured apart), and Goong when no resolver is wired.
 *
 * <p>The answer may name a provider without credentials; callers that must not fail check
 * {@link PlaceLookupProviders#isAvailable} themselves.
 */
@Component
public class PlaceLookupRouter {

    private final PlaceLookupProviders providers;
    private final MapProviderResolver resolver;

    /** @param resolver null in hand-built tests: inside Vietnam the answer is then always Goong */
    public PlaceLookupRouter(PlaceLookupProviders providers, MapProviderResolver resolver) {
        this.providers = providers;
        this.resolver = resolver;
    }

    public PlaceLookupProviderType providerFor(UUID userId, BigDecimal latitude, BigDecimal longitude) {
        if (!inVietnam(latitude, longitude) && providers.isAvailable(PlaceLookupProviderType.MAPBOX)) {
            return PlaceLookupProviderType.MAPBOX;
        }
        if (resolver == null) return PlaceLookupProviderType.GOONG;
        return resolver.forUser(userId)
                .map(provider -> provider == MapProvider.MAPBOX ? PlaceLookupProviderType.MAPBOX : PlaceLookupProviderType.GOONG)
                .orElse(PlaceLookupProviderType.GOONG);
    }

    /** Vietnam's mainland and near islands, the same box map search uses; no point counts as inside. */
    static boolean inVietnam(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) return true;
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        return lat >= 8.0 && lat <= 23.5 && lng >= 102.0 && lng <= 110.5;
    }
}

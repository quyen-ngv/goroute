package com.ds.goroute.service.placelookup;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Finds the {@link PlaceLookupProvider} registered for a provider type. */
@Component
public class PlaceLookupProviders {

    private final Map<PlaceLookupProviderType, PlaceLookupProvider> providersByType =
            new EnumMap<>(PlaceLookupProviderType.class);

    public PlaceLookupProviders(List<PlaceLookupProvider> providers) {
        for (PlaceLookupProvider provider : providers) {
            PlaceLookupProvider previous = providersByType.put(provider.type(), provider);
            if (previous != null) {
                throw new IllegalStateException("Two place lookup providers registered for " + provider.type());
            }
        }
    }

    /** Whether a provider of this type is registered and configured. */
    public boolean isAvailable(PlaceLookupProviderType type) {
        PlaceLookupProvider provider = providersByType.get(type);
        return provider != null && provider.isAvailable();
    }

    public PlaceLookupProvider get(PlaceLookupProviderType type) {
        PlaceLookupProvider provider = providersByType.get(type);
        if (provider == null) {
            throw new IllegalStateException("No place lookup provider registered for " + type);
        }
        return provider;
    }
}

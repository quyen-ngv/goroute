package com.ds.goroute.service.mapsearch;

import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.ds.goroute.thirdparty.mapbox.MapboxClient;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MapProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Picks the map provider for one user: beta users (USER/BETA_USER) read
 * {@code MAP_PROVIDER/BETA_PROVIDER}, everybody else {@code MAP_PROVIDER/USER_PROVIDER}.
 *
 * <p>A provider without a key is skipped, so a machine with only one key still works and a
 * machine with none answers from the catalogue alone. Config is read on every call, so an
 * operator's switch applies at once.
 */
@Service
@RequiredArgsConstructor
public class MapProviderResolver {

    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;
    private final MapboxClient mapbox;
    private final GoongClient goong;

    /** The provider to ask for this user; empty when no provider has a key. */
    public Optional<MapProvider> forUser(UUID userId) {
        BusinessConfigKey key = betaAccess.isBetaUser(userId)
                ? BusinessConfigKey.MAP_PROVIDER_BETA
                : BusinessConfigKey.MAP_PROVIDER_USER;
        return usable(config.getEnum(key, MapProvider.class));
    }

    /** Whether a failed Mapbox call may be retried with Goong. */
    public boolean mayFallBackToGoong() {
        return goong.isConfigured() && config.getBoolean(BusinessConfigKey.MAP_PROVIDER_FALLBACK_TO_GOONG);
    }

    Optional<MapProvider> usable(MapProvider chosen) {
        if (chosen == MapProvider.MAPBOX && mapbox.isConfigured()) {
            return Optional.of(MapProvider.MAPBOX);
        }
        if (goong.isConfigured()) {
            return Optional.of(MapProvider.GOONG);
        }
        if (mapbox.isConfigured()) {
            return Optional.of(MapProvider.MAPBOX);
        }
        return Optional.empty();
    }
}

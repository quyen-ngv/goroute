package com.ds.goroute.service.mapsearch;

import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.ds.goroute.thirdparty.mapbox.MapboxClient;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MapProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MapProviderResolver")
class MapProviderResolverTest {

    @Mock private BusinessConfigService config;
    @Mock private BetaAccessService beta;
    @Mock private MapboxClient mapbox;
    @Mock private GoongClient goong;
    @InjectMocks private MapProviderResolver resolver;

    private final UUID betaUser = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(beta.isBetaUser(betaUser)).thenReturn(true);
        when(config.getEnum(BusinessConfigKey.MAP_PROVIDER_BETA, MapProvider.class)).thenReturn(MapProvider.MAPBOX);
        when(config.getEnum(BusinessConfigKey.MAP_PROVIDER_USER, MapProvider.class)).thenReturn(MapProvider.GOONG);
        when(mapbox.isConfigured()).thenReturn(true);
        when(goong.isConfigured()).thenReturn(true);
    }

    @Test
    @DisplayName("beta users read BETA_PROVIDER, everybody else USER_PROVIDER")
    void cohorts() {
        assertThat(resolver.forUser(betaUser)).contains(MapProvider.MAPBOX);
        assertThat(resolver.forUser(otherUser)).contains(MapProvider.GOONG);
    }

    @Test
    @DisplayName("a provider without a key is skipped; with no key at all the answer is empty")
    void skipsUnconfigured() {
        when(mapbox.isConfigured()).thenReturn(false);
        assertThat(resolver.forUser(betaUser)).contains(MapProvider.GOONG);

        when(mapbox.isConfigured()).thenReturn(true);
        when(goong.isConfigured()).thenReturn(false);
        assertThat(resolver.forUser(otherUser)).contains(MapProvider.MAPBOX);

        when(mapbox.isConfigured()).thenReturn(false);
        assertThat(resolver.forUser(otherUser)).isEmpty();
    }

    @Test
    @DisplayName("falls back to Goong only when Goong has a key and the switch is on")
    void fallbackSwitch() {
        when(config.getBoolean(BusinessConfigKey.MAP_PROVIDER_FALLBACK_TO_GOONG)).thenReturn(true);
        assertThat(resolver.mayFallBackToGoong()).isTrue();

        when(goong.isConfigured()).thenReturn(false);
        assertThat(resolver.mayFallBackToGoong()).isFalse();
    }
}

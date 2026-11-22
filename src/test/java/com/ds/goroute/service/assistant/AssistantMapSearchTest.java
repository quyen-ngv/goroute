package com.ds.goroute.service.assistant;

import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.MapResponseCacheRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.placelookup.PlaceHit;
import com.ds.goroute.service.placelookup.PlaceLookupException;
import com.ds.goroute.service.placelookup.PlaceLookupProvider;
import com.ds.goroute.service.placelookup.PlaceLookupProviderType;
import com.ds.goroute.service.placelookup.PlaceLookupProviders;
import com.ds.goroute.service.placelookup.PlaceLookupQuery;
import com.ds.goroute.service.placelookup.PlaceLookupRouter;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantMapSearchTest {

    private static final BigDecimal LAT = new BigDecimal("21.0300");
    private static final BigDecimal LNG = new BigDecimal("105.8000");
    private static final UUID USER = UUID.randomUUID();

    private BusinessConfigService config;
    private MapResponseCacheRepository cache;
    private FakeGoong goong;
    private AssistantMapSearch search;

    @BeforeEach
    void setUp() {
        config = mock(BusinessConfigService.class);
        when(config.getBoolean(BusinessConfigKey.ASSISTANT_MAP_SEARCH_ENABLED)).thenReturn(true);
        cache = mock(MapResponseCacheRepository.class);
        when(cache.find(anyString(), any())).thenReturn(Optional.empty());
        goong = new FakeGoong();
        PlaceLookupProviders providers = new PlaceLookupProviders(List.of(goong));
        search = new AssistantMapSearch(config, new PlaceLookupRouter(providers, null), providers, cache,
                new ObjectMapper());
    }

    @Test
    void goongHitsTakeTheirCoordinatesFromDetailsForTheFirstThreeOnly() {
        for (int i = 1; i <= 5; i++) goong.hits.add(hit("g-" + i, "Lẩu bò " + i));

        AssistantMapSearch.Result result = search.search(USER, "  lẩu bò Ban Công  ", LAT, LNG, 15, 30, List.of());

        assertThat(result.provider()).isEqualTo(PlaceLookupProviderType.GOONG);
        assertThat(result.places()).extracting(AssistantMapPlace::providerPlaceId).containsExactly("g-1", "g-2", "g-3");
        assertThat(goong.detailCalls).isEqualTo(3);
        assertThat(goong.lastQuery.text()).isEqualTo("lẩu bò Ban Công");
        assertThat(goong.lastQuery.biasRadiusMeters()).isEqualTo(15_000);
        verify(cache).save(anyString(), eq(AssistantMapSearch.CACHE_OPERATION), eq("GOONG"), anyString(), any());
    }

    @Test
    void aCatalogueRowIsNotRepeatedFromTheMap() {
        goong.hits.add(hit("g-1", "Lẩu Bò Ban Công"));
        goong.hits.add(hit("g-2", "Bún chả Hương Liên"));
        Place same = Place.builder().id(UUID.randomUUID()).title("Lẩu bò Ban Công")
                .latitude(new BigDecimal("21.0400")).longitude(new BigDecimal("105.8100")).build();

        AssistantMapSearch.Result result = search.search(USER, "lẩu bò", LAT, LNG, 15, 30, List.of(same));

        assertThat(result.places()).extracting(AssistantMapPlace::name).containsExactly("Bún chả Hương Liên");
    }

    @Test
    void aProviderFailureMeansNoMapRowsAndIsNotCached() {
        goong.failSearch = true;

        AssistantMapSearch.Result result = search.search(USER, "lẩu bò", LAT, LNG, 15, 30, List.of());

        assertThat(result.places()).isEmpty();
        verify(cache, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    void aCachedAnswerCostsNoProviderCall() {
        when(cache.find(anyString(), any())).thenReturn(Optional.of(
                "[{\"provider\":\"GOONG\",\"providerPlaceId\":\"g-9\",\"name\":\"Lẩu bò 9\",\"lat\":\"21.031\",\"lng\":\"105.801\"}]"));

        AssistantMapSearch.Result result = search.search(USER, "lẩu bò", LAT, LNG, 15, 30, List.of());

        assertThat(result.places()).extracting(AssistantMapPlace::providerPlaceId).containsExactly("g-9");
        assertThat(goong.searchCalls).isZero();
    }

    @Test
    void switchedOffItDoesNotRun() {
        when(config.getBoolean(BusinessConfigKey.ASSISTANT_MAP_SEARCH_ENABLED)).thenReturn(false);

        assertThat(search.search(USER, "lẩu bò", LAT, LNG, 15, 30, List.of())).isNull();
        assertThat(goong.searchCalls).isZero();
    }

    private static PlaceHit hit(String id, String name) {
        return new PlaceHit(PlaceLookupProviderType.GOONG, id, name, name + ", Hà Nội", null, null, null, null);
    }

    /** Goong as the lookup sees it: autocomplete without coordinates, details with them. */
    private static final class FakeGoong implements PlaceLookupProvider {
        final List<PlaceHit> hits = new ArrayList<>();
        boolean failSearch;
        int searchCalls;
        int detailCalls;
        PlaceLookupQuery lastQuery;

        @Override
        public PlaceLookupProviderType type() {
            return PlaceLookupProviderType.GOONG;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public List<PlaceHit> searchText(PlaceLookupQuery query) {
            searchCalls++;
            lastQuery = query;
            if (failSearch) throw new PlaceLookupException("Goong autocomplete answered 502");
            return hits;
        }

        @Override
        public Optional<PlaceHit> getDetails(String providerPlaceId) {
            detailCalls++;
            PlaceHit found = hits.stream().filter(hit -> hit.providerPlaceId().equals(providerPlaceId)).findFirst().orElseThrow();
            return Optional.of(new PlaceHit(PlaceLookupProviderType.GOONG, providerPlaceId, found.name(), found.address(),
                    new BigDecimal("21.0310"), new BigDecimal("105.8010"), null, null));
        }
    }
}

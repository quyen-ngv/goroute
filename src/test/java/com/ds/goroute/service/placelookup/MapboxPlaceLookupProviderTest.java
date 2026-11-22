package com.ds.goroute.service.placelookup;

import com.ds.goroute.mapper.MapCacheMapper;
import com.ds.goroute.service.mapsearch.MapBudgetGuard;
import com.ds.goroute.thirdparty.mapbox.MapboxClient;
import com.ds.goroute.type.MapBillingApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapboxPlaceLookupProviderTest {

    private static final String ANSWER = """
            {"type":"FeatureCollection","features":[{"type":"Feature",
              "geometry":{"type":"Point","coordinates":[108.326,15.8772]},
              "properties":{"mapbox_id":"m1","name":"Chùa Cầu",
                "coordinates":{"latitude":15.8772,"longitude":108.326}}}]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MapboxClient mapbox;
    private MapBudgetGuard budget;
    private MapCacheMapper cache;
    private MapboxPlaceLookupProvider provider;

    @BeforeEach
    void setUp() {
        mapbox = mock(MapboxClient.class);
        budget = mock(MapBudgetGuard.class);
        cache = mock(MapCacheMapper.class);
        provider = new MapboxPlaceLookupProvider(mapbox, budget, cache, objectMapper);
    }

    @Test
    void paysForASearchOnceAndKeepsTheAnswer() throws Exception {
        when(budget.tryAcquire(MapBillingApi.SEARCH)).thenReturn(new MapBudgetGuard.Decision(true, null));
        when(mapbox.forward(eq("Chùa Cầu Hội An"), any(), any(), any(), anyInt())).thenReturn(objectMapper.readTree(ANSWER));

        List<PlaceHit> hits = provider.searchText(PlaceLookupQuery.text("Chùa Cầu Hội An", 3));

        assertThat(hits).extracting(PlaceHit::providerPlaceId).containsExactly("m1");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(cache).upsertResponse(anyString(), eq("PLACE_LOOKUP"), eq("MAPBOX"), body.capture(), any());
        assertThat(objectMapper.readTree(body.getValue())).isEqualTo(objectMapper.readTree(ANSWER));
    }

    @Test
    void answersTheSameSearchFromTheCacheWithoutSpendingBudget() {
        when(cache.findResponse(anyString(), any())).thenReturn(ANSWER);

        List<PlaceHit> hits = provider.searchText(PlaceLookupQuery.text("Chùa Cầu Hội An", 3));

        assertThat(hits).extracting(PlaceHit::name).containsExactly("Chùa Cầu");
        verify(budget, never()).tryAcquire(any());
        verify(mapbox, never()).forward(any(), any(), any(), any(), anyInt());
    }

    @Test
    void sameTextWithOtherAccentsSharesOneCacheKey() throws Exception {
        when(budget.tryAcquire(MapBillingApi.SEARCH)).thenReturn(new MapBudgetGuard.Decision(true, null));
        JsonNode answer = objectMapper.readTree(ANSWER);
        when(mapbox.forward(any(), any(), any(), any(), anyInt())).thenReturn(answer);

        provider.searchText(PlaceLookupQuery.text("Chùa Cầu", 3));
        provider.searchText(PlaceLookupQuery.text("chua cau", 3));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cache, org.mockito.Mockito.times(2)).findResponse(keys.capture(), any());
        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
    }
}

package com.ds.goroute.service.mapsearch;

import com.ds.goroute.entity.MapPlaceCacheEntry;
import com.ds.goroute.entity.Ward;
import com.ds.goroute.mapper.MapCacheMapper;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.ds.goroute.thirdparty.mapbox.MapboxClient;
import com.ds.goroute.thirdparty.mapbox.MapboxException;
import com.ds.goroute.type.MapBillingApi;
import com.ds.goroute.type.MapProvider;
import com.ds.goroute.type.MapSearchScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MapSearchService")
class MapSearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T08:00:00Z");
    private static final String FORWARD = """
            {"features":[
              {"geometry":{"coordinates":[106.698,10.7725]},
               "properties":{"name":"Chợ Bến Thành","mapbox_id":"bentthanh1","feature_type":"poi",
                 "full_address":"Chợ Bến Thành, Thành phố Hồ Chí Minh","coordinates":{"latitude":10.7725,"longitude":106.698}}},
              {"geometry":{"coordinates":[106.481,10.638]},
               "properties":{"name":"Chợ Bến Lức","mapbox_id":"benluc1","feature_type":"poi",
                 "coordinates":{"latitude":10.638,"longitude":106.481}}}
            ]}""";

    @Mock private MapProviderResolver resolver;
    @Mock private MapboxClient mapbox;
    @Mock private GoongClient goong;
    @Mock private MapBudgetGuard budget;
    @Mock private MapCacheMapper cache;
    @Mock private GeoService geoService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID user = UUID.randomUUID();
    private MapSearchService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MapSearchService(resolver, mapbox, goong, budget, cache, geoService, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(resolver.forUser(any())).thenReturn(Optional.of(MapProvider.MAPBOX));
        when(resolver.mayFallBackToGoong()).thenReturn(true);
        when(mapbox.isConfigured()).thenReturn(true);
        when(budget.tryAcquire(any())).thenReturn(new MapBudgetGuard.Decision(true, null));
        when(cache.searchPicked(anyString(), any(), any(), anyDouble(), anyInt())).thenReturn(List.of());
        when(mapbox.forward(anyString(), any(), any(), any(), anyInt())).thenReturn(objectMapper.readTree(FORWARD));
        when(goong.forward(anyString(), any())).thenReturn(ResponseEntity.ok("{\"predictions\":[],\"from\":\"goong\"}"));
        when(geoService.resolveWard(any(), any())).thenReturn(Optional.empty());
    }

    private MapSearchService.AutocompleteQuery query(String input, String location, Integer radiusKm) {
        return new MapSearchService.AutocompleteQuery(input, location, radiusKm, 5, null, null, "vi-VN,vi;q=0.9",
                MapSearchScope.ALL);
    }

    private MapSearchService.AutocompleteQuery areaQuery(String input, String location) {
        return new MapSearchService.AutocompleteQuery(input, location, 30, null, null, null, "vi-VN,vi;q=0.9",
                MapSearchScope.AREA);
    }

    private JsonNode body(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    @Test
    @DisplayName("a Goong user is forwarded to Goong exactly as before")
    void goongCohort() {
        when(resolver.forUser(user)).thenReturn(Optional.of(MapProvider.GOONG));

        ResponseEntity<String> response = service.autocomplete(user, query("ben thanh", "10.77,106.69", null));

        assertThat(response.getBody()).contains("goong");
        verify(goong).forward(eq("/Place/AutoComplete"), any(MultiValueMap.class));
        verify(mapbox, never()).forward(anyString(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("a Mapbox answer is shaped like Goong's, kept, and cached for the next caller")
    void mapboxAnswerIsShapedKeptAndCached() throws Exception {
        JsonNode answer = body(service.autocomplete(user, query("chợ bến", "10.77,106.69", null)));

        assertThat(answer.path("predictions")).hasSize(2);
        assertThat(answer.path("predictions").get(0).path("place_id").asText()).isEqualTo("mapbox:bentthanh1");
        verify(mapbox).forward("chợ bến", 10.77, 106.69, "vi", 5);
        verify(cache).upsertPlace(org.mockito.ArgumentMatchers.argThat(place -> "bentthanh1".equals(place.getProviderPlaceId())));
        verify(cache).upsertResponse(anyString(), eq("AUTOCOMPLETE"), eq("MAPBOX"), anyString(), any());
    }

    @Test
    @DisplayName("an area search asks Geocoding for areas only, ignores the radius and uses the geocode budget")
    void areaSearchUsesGeocoding() throws Exception {
        String areas = """
                {"features":[{"geometry":{"coordinates":[103.8,1.35]},
                  "properties":{"name":"Singapore","mapbox_id":"sg1","feature_type":"country",
                    "coordinates":{"latitude":1.35,"longitude":103.8}}}]}""";
        when(mapbox.geocodeAreas(anyString(), any(), any(), any(), anyInt())).thenReturn(objectMapper.readTree(areas));

        JsonNode answer = body(service.autocomplete(user, areaQuery("singapore", "21.03,105.85")));

        assertThat(answer.path("predictions")).hasSize(1);
        assertThat(answer.path("predictions").get(0).path("place_id").asText()).isEqualTo("mapbox:sg1");
        verify(mapbox).geocodeAreas("singapore", 21.03, 105.85, "vi", MapSearchService.AUTOCOMPLETE_DEFAULT_LIMIT);
        verify(budget).tryAcquire(MapBillingApi.GEOCODE);
        verify(mapbox, never()).forward(anyString(), any(), any(), any(), anyInt());
        verify(cache, never()).searchPicked(anyString(), any(), any(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("a failed area search inside Vietnam falls back to Goong")
    void areaSearchFallsBackToGoong() {
        when(mapbox.geocodeAreas(anyString(), any(), any(), any(), anyInt()))
                .thenThrow(new MapboxException("boom", 500));

        ResponseEntity<String> response = service.autocomplete(user, areaQuery("da lat", "11.94,108.45"));

        assertThat(response.getBody()).contains("goong");
    }

    @Test
    @DisplayName("a cached answer is returned without spending the budget")
    void cachedAnswer() {
        when(cache.findResponse(anyString(), any())).thenReturn("{\"status\":\"OK\",\"predictions\":[]}");

        ResponseEntity<String> response = service.autocomplete(user, query("cho ben", null, null));

        assertThat(response.getBody()).contains("predictions");
        verify(budget, never()).tryAcquire(any());
        verify(mapbox, never()).forward(anyString(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("enough places users already chose answer without Mapbox")
    void pickedPlacesAnswer() throws Exception {
        List<MapPlaceCacheEntry> picked = MapboxResults.places(objectMapper.readTree(FORWARD));
        picked = List.of(picked.get(0), picked.get(1), picked.get(0), picked.get(1), picked.get(0));
        when(cache.searchPicked(eq("cho ben"), any(), any(), anyDouble(), anyInt())).thenReturn(picked);

        JsonNode answer = body(service.autocomplete(user, query("chợ bến", null, null)));

        assertThat(answer.path("predictions")).hasSize(5);
        verify(mapbox, never()).forward(anyString(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("a spent budget answers from saved places, flagged saving_mode with the end time")
    void savingMode() throws Exception {
        when(budget.tryAcquire(MapBillingApi.SEARCH))
                .thenReturn(new MapBudgetGuard.Decision(false, Instant.parse("2026-10-12T00:00:00Z")));

        JsonNode answer = body(service.autocomplete(user, query("chợ bến", null, null)));

        assertThat(answer.path("saving_mode").asBoolean()).isTrue();
        assertThat(answer.path("saving_mode_until").asText()).isEqualTo("2026-10-12T00:00:00Z");
        verify(mapbox, never()).forward(anyString(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("a radius keeps only results inside it, measured on the server")
    void radiusFilter() throws Exception {
        JsonNode answer = body(service.autocomplete(user, query("chợ bến", "10.7725,106.698", 5)));

        assertThat(answer.path("predictions")).hasSize(1);
        assertThat(answer.path("predictions").get(0).path("place_id").asText()).isEqualTo("mapbox:bentthanh1");
    }

    @Test
    @DisplayName("Mapbox failing in Vietnam falls back to Goong; abroad it reports the failure")
    void fallback() {
        when(mapbox.forward(anyString(), any(), any(), any(), anyInt()))
                .thenThrow(new MapboxException("Mapbox forward answered 503", 503));

        assertThat(service.autocomplete(user, query("chợ bến", "10.77,106.69", null)).getBody()).contains("goong");

        ResponseEntity<String> abroad = service.autocomplete(user, query("louvre", "48.86,2.33", null));
        assertThat(abroad.getStatusCode().value()).isEqualTo(502);
    }

    @Test
    @DisplayName("no provider at all answers from the catalogue alone, without error")
    void catalogueOnly() throws Exception {
        when(resolver.forUser(user)).thenReturn(Optional.empty());

        JsonNode answer = body(service.autocomplete(user, query("chợ bến", null, null)));

        assertThat(answer.path("predictions")).isEmpty();
        verify(budget, never()).tryAcquire(any());
    }

    @Test
    @DisplayName("a known mapbox: id is answered from the place cache, marked picked, at no cost")
    void detailFromCache() throws Exception {
        MapPlaceCacheEntry place = MapboxResults.places(objectMapper.readTree(FORWARD)).get(0);
        when(cache.findPlace("MAPBOX", "bentthanh1")).thenReturn(place);
        Ward ward = new Ward();
        ward.setName("Phường Bến Thành");
        ward.setProvinceName("Thành phố Hồ Chí Minh");
        when(geoService.resolveWard(any(), any())).thenReturn(Optional.of(ward));

        JsonNode result = body(service.placeDetail("mapbox:bentthanh1", null, "vi")).path("result");

        assertThat(result.path("name").asText()).isEqualTo("Chợ Bến Thành");
        assertThat(result.path("compound").path("commune").asText()).isEqualTo("Phường Bến Thành");
        verify(cache).incrementPicked("MAPBOX", "bentthanh1");
        verify(mapbox, never()).retrieve(anyString(), any());
        verify(budget, never()).tryAcquire(any());
    }

    @Test
    @DisplayName("a Goong id stays with Goong whatever the cohort; a malformed mapbox id is not sent anywhere")
    void detailRouting() throws Exception {
        service.placeDetail("goong-abc", null, null);
        verify(goong).forward(eq("/Place/Detail"), any(MultiValueMap.class));

        JsonNode answer = body(service.placeDetail("mapbox:../../etc", null, null));
        assertThat(answer.path("result").isNull()).isTrue();
        verify(mapbox, never()).retrieve(anyString(), any());
    }

    @Test
    @DisplayName("reverse geocoding asks Mapbox for the grid cell and caches the answer")
    void reverse() throws Exception {
        when(mapbox.reverse(anyDouble(), anyDouble(), any(), anyInt())).thenReturn(objectMapper.readTree(FORWARD));

        JsonNode answer = body(service.geocode(user, null, "10.77251,106.69802", 5, "vi"));

        assertThat(answer.path("results")).hasSize(2);
        verify(mapbox).reverse(10.7725, 106.698, "vi", 5);
        verify(cache).upsertResponse(anyString(), eq("REVERSE"), eq("MAPBOX"), anyString(), any());
    }

    @Test
    @DisplayName("forward geocoding (a city name) uses the Geocoding budget")
    void forwardGeocode() throws Exception {
        when(mapbox.geocodeForward(anyString(), any(), anyInt())).thenReturn(objectMapper.readTree(FORWARD));

        JsonNode answer = body(service.geocode(user, "Đà Lạt", null, null, "vi"));

        assertThat(answer.path("results").get(0).path("geometry").path("location").path("lat").asDouble())
                .isEqualTo(10.7725);
        verify(budget).tryAcquire(MapBillingApi.GEOCODE);
        verify(mapbox).geocodeForward("Đà Lạt", "vi", 1);
    }

    @Test
    @DisplayName("parses language, points and the Vietnam box")
    void helpers() {
        assertThat(MapSearchService.language("vi-VN,vi;q=0.9,en;q=0.8")).isEqualTo("vi");
        assertThat(MapSearchService.language("zh-TW")).isEqualTo("zh");
        assertThat(MapSearchService.language(null)).isEqualTo("vi");
        assertThat(MapSearchService.parsePoint("10.77, 106.69")).containsExactly(10.77, 106.69);
        assertThat(MapSearchService.parsePoint("91,0")).isNull();
        assertThat(MapSearchService.parsePoint("abc")).isNull();
        assertThat(MapSearchService.inVietnam(21.03, 105.85)).isTrue();
        assertThat(MapSearchService.inVietnam(48.86, 2.33)).isFalse();
    }

}

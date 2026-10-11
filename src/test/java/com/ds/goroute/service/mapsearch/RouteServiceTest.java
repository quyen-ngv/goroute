package com.ds.goroute.service.mapsearch;

import com.ds.goroute.dto.request.RouteDayRequest;
import com.ds.goroute.dto.response.RouteDayResponse;
import com.ds.goroute.entity.RouteLegCacheEntry;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.MapCacheMapper;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.ds.goroute.thirdparty.mapbox.MapboxClient;
import com.ds.goroute.thirdparty.mapbox.MapboxException;
import com.ds.goroute.type.MapBillingApi;
import com.ds.goroute.type.RouteProfile;
import com.ds.goroute.utils.PolylineCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RouteService")
class RouteServiceTest {

    @Mock private MapboxClient mapbox;
    @Mock private GoongClient goong;
    @Mock private MapProviderResolver resolver;
    @Mock private MapBudgetGuard budget;
    @Mock private MapCacheMapper cache;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RouteService service;

    @BeforeEach
    void setUp() {
        service = new RouteService(mapbox, goong, resolver, budget, cache, objectMapper);
        when(mapbox.isConfigured()).thenReturn(true);
        when(budget.tryAcquire(any())).thenReturn(new MapBudgetGuard.Decision(true, null));
        when(cache.findLegs(anyList())).thenReturn(List.of());
        when(resolver.mayFallBackToGoong()).thenReturn(true);
    }

    private static RouteDayRequest request(double[][] points, RouteProfile... profiles) {
        RouteDayRequest request = new RouteDayRequest();
        List<RouteDayRequest.RoutePoint> list = new ArrayList<>();
        for (double[] point : points) {
            RouteDayRequest.RoutePoint routePoint = new RouteDayRequest.RoutePoint();
            routePoint.setLatitude(point[0]);
            routePoint.setLongitude(point[1]);
            list.add(routePoint);
        }
        request.setPoints(list);
        request.setProfiles(List.of(profiles));
        return request;
    }

    private static final double[][] HANOI = {{21.0287, 105.8524}, {21.0368, 105.8349}, {21.0245, 105.8412}, {21.0300, 105.8500}};

    private String directions(int legs) throws Exception {
        StringBuilder json = new StringBuilder("{\"code\":\"Ok\",\"routes\":[{\"legs\":[");
        for (int leg = 0; leg < legs; leg++) {
            String geometry = PolylineCodec.encode(List.of(new double[]{21.0 + leg, 105.0}, new double[]{21.001 + leg, 105.001}), 6);
            json.append(leg == 0 ? "" : ",")
                    .append("{\"distance\":").append(1000 + leg).append(".4,\"duration\":").append(300 + leg)
                    .append(",\"steps\":[{\"geometry\":").append(objectMapper.writeValueAsString(geometry)).append("}]}");
        }
        return json.append("]}]}").toString();
    }

    @Test
    @DisplayName("consecutive legs with one profile are ONE Mapbox request, and every leg is cached")
    void groupsLegsIntoOneRequest() throws Exception {
        when(mapbox.directions(eq("driving"), anyList())).thenReturn(objectMapper.readTree(directions(3)));

        RouteDayResponse response = service.day(request(HANOI, RouteProfile.DRIVING, RouteProfile.DRIVING, RouteProfile.DRIVING));

        assertThat(response.legs()).extracting(RouteDayResponse.Leg::distanceMeters).containsExactly(1000, 1001, 1002);
        assertThat(response.legs()).extracting(RouteDayResponse.Leg::source).containsOnly("MAPBOX");
        verify(mapbox, times(1)).directions(eq("driving"), anyList());
        verify(budget, times(1)).tryAcquire(MapBillingApi.DIRECTIONS);
        verify(cache, times(3)).upsertLeg(any());
    }

    @Test
    @DisplayName("a change of profile starts a new request; straight legs (flights) are never routed")
    void splitsByProfile() throws Exception {
        when(mapbox.directions(anyString(), anyList()))
                .thenReturn(objectMapper.readTree(directions(1)));

        RouteDayResponse response = service.day(request(HANOI, RouteProfile.WALKING, RouteProfile.STRAIGHT, RouteProfile.DRIVING));

        verify(mapbox).directions(eq("walking"), anyList());
        verify(mapbox).directions(eq("driving"), anyList());
        RouteDayResponse.Leg flight = response.legs().get(1);
        assertThat(flight.source()).isEqualTo("STRAIGHT");
        assertThat(flight.durationSeconds()).isNull();
        assertThat(flight.distanceMeters()).isPositive();
    }

    @Test
    @DisplayName("cached legs cost nothing; only the missing one is routed")
    void usesCache() throws Exception {
        RouteDayRequest request = request(HANOI, RouteProfile.DRIVING, RouteProfile.DRIVING, RouteProfile.DRIVING);
        String first = RouteService.legKey(RouteProfile.DRIVING, request.getPoints().get(0), request.getPoints().get(1));
        String last = RouteService.legKey(RouteProfile.DRIVING, request.getPoints().get(2), request.getPoints().get(3));
        when(cache.findLegs(anyList())).thenReturn(List.of(
                RouteLegCacheEntry.builder().legKey(first).provider("MAPBOX").distanceM(10).durationS(20).polyline("p").build(),
                RouteLegCacheEntry.builder().legKey(last).provider("MAPBOX").distanceM(30).durationS(40).polyline("q").build()));
        when(mapbox.directions(anyString(), anyList())).thenReturn(objectMapper.readTree(directions(1)));

        RouteDayResponse response = service.day(request);

        assertThat(response.legs()).extracting(RouteDayResponse.Leg::distanceMeters).containsExactly(10, 1000, 30);
        ArgumentCaptor<List<double[]>> points = ArgumentCaptor.forClass(List.class);
        verify(mapbox).directions(eq("driving"), points.capture());
        assertThat(points.getValue()).hasSize(2);
        verify(cache).touchLegs(any());
    }

    @Test
    @DisplayName("a spent budget draws straight lines and says when routes come back")
    void savingMode() {
        when(budget.tryAcquire(MapBillingApi.DIRECTIONS))
                .thenReturn(new MapBudgetGuard.Decision(false, Instant.parse("2026-10-12T00:00:00Z")));

        RouteDayResponse response = service.day(request(HANOI, RouteProfile.DRIVING, RouteProfile.DRIVING, RouteProfile.DRIVING));

        assertThat(response.savingMode()).isTrue();
        assertThat(response.savingModeUntil()).isEqualTo("2026-10-12T00:00:00Z");
        assertThat(response.legs()).extracting(RouteDayResponse.Leg::source).containsOnly("STRAIGHT");
        verify(mapbox, never()).directions(anyString(), anyList());
    }

    @Test
    @DisplayName("Mapbox failing in Vietnam asks Goong per leg; abroad it draws straight lines")
    void fallback() {
        when(mapbox.directions(anyString(), anyList())).thenThrow(new MapboxException("503", 503));
        String goongRoute = "{\"routes\":[{\"legs\":[{\"distance\":{\"value\":1500},\"duration\":{\"value\":420}}],"
                + "\"overview_polyline\":{\"points\":\"_p~iF~ps|U_ulLnnqC\"}}]}";
        when(goong.forward(eq("/Direction"), any())).thenReturn(ResponseEntity.ok(goongRoute));

        RouteDayResponse vietnam = service.day(request(new double[][]{{21.0287, 105.8524}, {21.0368, 105.8349}}, RouteProfile.DRIVING));
        assertThat(vietnam.legs().get(0).source()).isEqualTo("GOONG");
        assertThat(vietnam.legs().get(0).durationSeconds()).isEqualTo(420);

        RouteDayResponse paris = service.day(request(new double[][]{{48.86, 2.33}, {48.85, 2.35}}, RouteProfile.DRIVING));
        assertThat(paris.legs().get(0).source()).isEqualTo("STRAIGHT");
    }

    @Test
    @DisplayName("profiles must hold one entry per leg")
    void validatesProfiles() {
        assertThatThrownBy(() -> service.day(request(HANOI, RouteProfile.DRIVING)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("polylines round-trip at precision 5 and 6")
    void polylineRoundTrip() {
        List<double[]> line = List.of(new double[]{38.5, -120.2}, new double[]{40.7, -120.95}, new double[]{43.252, -126.453});
        assertThat(PolylineCodec.encode(line, 5)).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        List<double[]> decoded = PolylineCodec.decode(PolylineCodec.encode(line, 6), 6);
        assertThat(decoded).hasSize(3);
        assertThat(decoded.get(2)[1]).isEqualTo(-126.453);
    }
}

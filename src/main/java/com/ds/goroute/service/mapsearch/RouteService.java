package com.ds.goroute.service.mapsearch;

import com.ds.goroute.constant.ErrorConstant;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Road legs for one day of a trip: distance, travel time and the line to draw, from one call.
 *
 * <p>Every leg is looked up in {@code route_leg_cache} first; the cache is shared by all users
 * and trips, so a public trip viewed by many people is routed once. Missing legs are grouped
 * into runs of consecutive legs with the same profile, and each run is ONE Mapbox Directions
 * request (billed per request, up to 25 points). Mapbox failing inside Vietnam falls back to
 * Goong; otherwise, or when the budget is spent, a leg is a straight line with no time.
 *
 * <p>Mapbox routes everyone today, as the app did before routing moved to the server; the
 * MAP_PROVIDER cohort rows decide search and geocoding only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    /** Mapbox accepts 25 points per request, so a run holds at most 24 legs. */
    static final int MAX_LEGS_PER_REQUEST = 24;

    private final MapboxClient mapbox;
    private final GoongClient goong;
    private final MapProviderResolver resolver;
    private final MapBudgetGuard budget;
    private final MapCacheMapper cache;
    private final ObjectMapper objectMapper;

    public RouteDayResponse day(RouteDayRequest request) {
        List<RouteDayRequest.RoutePoint> points = request.getPoints();
        if (request.getProfiles().size() != points.size() - 1) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "profiles must hold one entry per leg (points - 1)");
        }
        int legCount = points.size() - 1;
        RouteDayResponse.Leg[] legs = new RouteDayResponse.Leg[legCount];
        String[] keys = new String[legCount];
        for (int index = 0; index < legCount; index++) {
            RouteProfile profile = request.getProfiles().get(index);
            RouteDayRequest.RoutePoint from = points.get(index);
            RouteDayRequest.RoutePoint to = points.get(index + 1);
            if (profile == RouteProfile.STRAIGHT || samePoint(from, to)) {
                legs[index] = straight(index, from, to);
            } else {
                keys[index] = legKey(profile, from, to);
            }
        }
        useCachedLegs(legs, keys);

        Instant savingUntil = null;
        int index = 0;
        while (index < legCount) {
            if (legs[index] != null) {
                index++;
                continue;
            }
            RouteProfile profile = request.getProfiles().get(index);
            int end = index;
            while (end + 1 < legCount && legs[end + 1] == null && request.getProfiles().get(end + 1) == profile
                    && end + 1 - index < MAX_LEGS_PER_REQUEST) {
                end++;
            }
            Instant refusedUntil = routeRun(points, profile, index, end, legs, keys);
            if (refusedUntil != null) {
                savingUntil = refusedUntil;
            }
            index = end + 1;
        }
        return new RouteDayResponse(List.of(legs), savingUntil != null,
                savingUntil == null ? null : savingUntil.toString());
    }

    /** Routes legs {@code first..last}; returns when saving mode ends if the budget refused. */
    private Instant routeRun(List<RouteDayRequest.RoutePoint> points, RouteProfile profile, int first, int last,
                             RouteDayResponse.Leg[] legs, String[] keys) {
        List<RouteDayRequest.RoutePoint> runPoints = points.subList(first, last + 2);
        if (mapbox.isConfigured()) {
            MapBudgetGuard.Decision decision = budget.tryAcquire(MapBillingApi.DIRECTIONS);
            if (!decision.allowed()) {
                fillStraight(points, first, last, legs);
                return decision.resumesAt();
            }
            try {
                List<double[]> lngLat = runPoints.stream()
                        .map(point -> new double[]{point.getLongitude(), point.getLatitude()})
                        .toList();
                JsonNode answer = mapbox.directions(profile == RouteProfile.WALKING ? "walking" : "driving", lngLat);
                if (storeMapboxLegs(answer, points, profile, first, last, legs, keys)) {
                    return null;
                }
            } catch (MapboxException exception) {
                log.warn("Mapbox directions failed for {} legs: {}", last - first + 1, exception.getMessage());
            }
        }
        if (resolver.mayFallBackToGoong() && runPoints.stream().allMatch(
                point -> MapSearchService.inVietnam(point.getLatitude(), point.getLongitude()))) {
            for (int index = first; index <= last; index++) {
                if (!routeWithGoong(points.get(index), points.get(index + 1), profile, index, legs, keys)) {
                    legs[index] = straight(index, points.get(index), points.get(index + 1));
                }
            }
            return null;
        }
        fillStraight(points, first, last, legs);
        return null;
    }

    private boolean storeMapboxLegs(JsonNode answer, List<RouteDayRequest.RoutePoint> points, RouteProfile profile,
                                    int first, int last, RouteDayResponse.Leg[] legs, String[] keys) {
        JsonNode route = answer.path("routes").path(0);
        JsonNode routeLegs = route.path("legs");
        if (!"Ok".equals(answer.path("code").asText("Ok")) || !routeLegs.isArray()
                || routeLegs.size() != last - first + 1) {
            return false;
        }
        for (int offset = 0; offset < routeLegs.size(); offset++) {
            JsonNode leg = routeLegs.get(offset);
            List<double[]> line = new ArrayList<>();
            for (JsonNode step : leg.path("steps")) {
                List<double[]> stepPoints = PolylineCodec.decode(step.path("geometry").asText(""), 6);
                if (!line.isEmpty() && !stepPoints.isEmpty() && samePoint(line.get(line.size() - 1), stepPoints.get(0))) {
                    stepPoints = stepPoints.subList(1, stepPoints.size());
                }
                line.addAll(stepPoints);
            }
            int index = first + offset;
            legs[index] = remember("MAPBOX", profile, index, points.get(index), points.get(index + 1),
                    (int) Math.round(leg.path("distance").asDouble()),
                    (int) Math.round(leg.path("duration").asDouble()),
                    PolylineCodec.encode(line, 6), keys[index]);
        }
        return true;
    }

    private boolean routeWithGoong(RouteDayRequest.RoutePoint from, RouteDayRequest.RoutePoint to, RouteProfile profile,
                                   int index, RouteDayResponse.Leg[] legs, String[] keys) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("origin", coordinate(from));
            params.add("destination", coordinate(to));
            params.add("vehicle", profile == RouteProfile.WALKING ? "bike" : "car");
            ResponseEntity<String> response = goong.forward("/Direction", params);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return false;
            }
            JsonNode route = objectMapper.readTree(response.getBody()).path("routes").path(0);
            JsonNode leg = route.path("legs").path(0);
            if (!leg.path("distance").path("value").isNumber()) {
                return false;
            }
            String polyline = PolylineCodec.encode(
                    PolylineCodec.decode(route.path("overview_polyline").path("points").asText(""), 5), 6);
            legs[index] = remember("GOONG", profile, index, from, to,
                    leg.path("distance").path("value").asInt(),
                    leg.path("duration").path("value").asInt(), polyline, keys[index]);
            return true;
        } catch (Exception exception) {
            log.warn("Goong direction failed for leg {}: {}", index, exception.getMessage());
            return false;
        }
    }

    private RouteDayResponse.Leg remember(String provider, RouteProfile profile, int index,
                                          RouteDayRequest.RoutePoint from, RouteDayRequest.RoutePoint to,
                                          int distance, int duration, String polyline, String key) {
        try {
            cache.upsertLeg(RouteLegCacheEntry.builder()
                    .legKey(key).provider(provider).profile(profile.name())
                    .fromLat(from.getLatitude()).fromLng(from.getLongitude())
                    .toLat(to.getLatitude()).toLng(to.getLongitude())
                    .distanceM(distance).durationS(duration).polyline(polyline)
                    .build());
        } catch (RuntimeException exception) {
            log.warn("Route leg cache write failed: {}", exception.getMessage());
        }
        return new RouteDayResponse.Leg(index, distance, duration, polyline, provider);
    }

    private void useCachedLegs(RouteDayResponse.Leg[] legs, String[] keys) {
        List<String> wanted = new ArrayList<>();
        for (String key : keys) {
            if (key != null) {
                wanted.add(key);
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        try {
            Map<String, RouteLegCacheEntry> found = new HashMap<>();
            cache.findLegs(wanted).forEach(entry -> found.put(entry.getLegKey(), entry));
            for (int index = 0; index < keys.length; index++) {
                RouteLegCacheEntry entry = keys[index] == null ? null : found.get(keys[index]);
                if (entry != null) {
                    legs[index] = new RouteDayResponse.Leg(index, entry.getDistanceM(), entry.getDurationS(),
                            entry.getPolyline(), entry.getProvider());
                }
            }
            if (!found.isEmpty()) {
                cache.touchLegs(found.keySet());
            }
        } catch (RuntimeException exception) {
            log.warn("Route leg cache read failed: {}", exception.getMessage());
        }
    }

    private void fillStraight(List<RouteDayRequest.RoutePoint> points, int first, int last, RouteDayResponse.Leg[] legs) {
        for (int index = first; index <= last; index++) {
            legs[index] = straight(index, points.get(index), points.get(index + 1));
        }
    }

    private static RouteDayResponse.Leg straight(int index, RouteDayRequest.RoutePoint from, RouteDayRequest.RoutePoint to) {
        double meters = MapboxResults.distanceMeters(from.getLatitude(), from.getLongitude(),
                to.getLatitude(), to.getLongitude());
        return new RouteDayResponse.Leg(index, (int) Math.round(meters), null, null, "STRAIGHT");
    }

    /** Endpoints rounded to 5 decimals (~1 m): the same two places always share one row. */
    static String legKey(RouteProfile profile, RouteDayRequest.RoutePoint from, RouteDayRequest.RoutePoint to) {
        return MapText.key("LEG", profile.name(), round5(from.getLatitude()), round5(from.getLongitude()),
                round5(to.getLatitude()), round5(to.getLongitude()));
    }

    private static String round5(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static String coordinate(RouteDayRequest.RoutePoint point) {
        return String.format(Locale.ROOT, "%.6f,%.6f", point.getLatitude(), point.getLongitude());
    }

    private static boolean samePoint(RouteDayRequest.RoutePoint a, RouteDayRequest.RoutePoint b) {
        return round5(a.getLatitude()).equals(round5(b.getLatitude()))
                && round5(a.getLongitude()).equals(round5(b.getLongitude()));
    }

    private static boolean samePoint(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < 1e-7 && Math.abs(a[1] - b[1]) < 1e-7;
    }
}

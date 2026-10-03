package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.service.MapSearchRateLimiter;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.ds.goroute.type.MapSearchOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Proxies Goong Maps APIs so the Goong key stays server-side. Requires an
 * authenticated app user (default security rule), which also prevents the
 * proxy itself from being abused to drain the Goong quota.
 *
 * <p>Autocomplete and place detail are also capped per user by {@link MapSearchRateLimiter}
 * (config label {@code MAP_SEARCH}); a refused call answers 429 and never reaches Goong.
 */
@RestController
@RequestMapping("/v1/api/goong")
@RequiredArgsConstructor
public class GoongController {

    private final GoongClient goongClient;
    private final MapSearchRateLimiter rateLimiter;

    @GetMapping("/autocomplete")
    public ResponseEntity<String> autocomplete(
            @RequestParam String input,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sessiontoken,
            @RequestParam(required = false) Boolean more_compound,
            @CurrentUser UUID userId,
            Authentication authentication) {
        rateLimiter.acquire(userId, isAdmin(authentication), MapSearchOperation.AUTOCOMPLETE);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("input", input);
        if (location != null) params.add("location", location);
        if (radius != null) params.add("radius", String.valueOf(radius));
        if (limit != null) params.add("limit", String.valueOf(limit));
        if (sessiontoken != null) params.add("sessiontoken", sessiontoken);
        if (more_compound != null) params.add("more_compound", String.valueOf(more_compound));
        return goongClient.forward("/Place/AutoComplete", params);
    }

    @GetMapping("/place-detail")
    public ResponseEntity<String> placeDetail(
            @RequestParam("place_id") String placeId,
            @RequestParam(required = false) String sessiontoken,
            @CurrentUser UUID userId,
            Authentication authentication) {
        rateLimiter.acquire(userId, isAdmin(authentication), MapSearchOperation.PLACE_DETAIL);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("place_id", placeId);
        if (sessiontoken != null) params.add("sessiontoken", sessiontoken);
        return goongClient.forward("/Place/Detail", params);
    }

    @GetMapping("/geocode")
    public ResponseEntity<String> geocode(
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String latlng,
            @RequestParam(required = false) Integer limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (address != null) params.add("address", address);
        if (latlng != null) params.add("latlng", latlng);
        // Undocumented on Goong's side but honoured: without it reverse geocoding
        // answers with a single result, which is not enough for the check-in picker
        // to offer a choice of nearby places. Goong caps the value at five.
        if (limit != null) params.add("limit", String.valueOf(limit));
        return goongClient.forward("/Geocode", params);
    }

    @GetMapping("/distance-matrix")
    public ResponseEntity<String> distanceMatrix(
            @RequestParam String origins,
            @RequestParam String destinations,
            @RequestParam(defaultValue = "car") String vehicle) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("origins", origins);
        params.add("destinations", destinations);
        params.add("vehicle", vehicle);
        return goongClient.forward("/DistanceMatrix", params);
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}

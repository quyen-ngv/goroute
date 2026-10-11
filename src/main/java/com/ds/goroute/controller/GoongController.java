package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.service.MapSearchRateLimiter;
import com.ds.goroute.service.mapsearch.MapSearchService;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.ds.goroute.type.MapSearchOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The app's map search proxy. Keeps provider keys server-side and answers in Goong's JSON
 * shape whichever provider the caller's cohort uses (Goong or Mapbox, see
 * {@link MapSearchService}), so installed apps never have to change.
 *
 * <p>Requires an authenticated app user (default security rule). Every call is capped per user
 * by {@link MapSearchRateLimiter} (config label {@code MAP_SEARCH}); a refused call answers 429
 * with the time it may be retried, and never reaches a provider.
 */
@RestController
@RequestMapping("/v1/api/goong")
@RequiredArgsConstructor
public class GoongController {

    private final GoongClient goongClient;
    private final MapSearchService mapSearchService;
    private final MapSearchRateLimiter rateLimiter;

    @GetMapping("/autocomplete")
    public ResponseEntity<String> autocomplete(
            @RequestParam String input,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sessiontoken,
            @RequestParam(required = false) Boolean more_compound,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @CurrentUser UUID userId,
            Authentication authentication) {
        rateLimiter.acquire(userId, isAdmin(authentication), MapSearchOperation.AUTOCOMPLETE);
        return mapSearchService.autocomplete(userId, new MapSearchService.AutocompleteQuery(
                input, location, radius, limit, sessiontoken, more_compound, acceptLanguage));
    }

    @GetMapping("/place-detail")
    public ResponseEntity<String> placeDetail(
            @RequestParam("place_id") String placeId,
            @RequestParam(required = false) String sessiontoken,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @CurrentUser UUID userId,
            Authentication authentication) {
        rateLimiter.acquire(userId, isAdmin(authentication), MapSearchOperation.PLACE_DETAIL);
        return mapSearchService.placeDetail(placeId, sessiontoken, acceptLanguage);
    }

    @GetMapping("/geocode")
    public ResponseEntity<String> geocode(
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String latlng,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @CurrentUser UUID userId,
            Authentication authentication) {
        rateLimiter.acquire(userId, isAdmin(authentication), MapSearchOperation.GEOCODE);
        return mapSearchService.geocode(userId, address, latlng, limit, acceptLanguage);
    }

    /** No app version calls this any more; kept for older builds, Goong only, and rate limited. */
    @GetMapping("/distance-matrix")
    public ResponseEntity<String> distanceMatrix(
            @RequestParam String origins,
            @RequestParam String destinations,
            @RequestParam(defaultValue = "car") String vehicle,
            @CurrentUser UUID userId,
            Authentication authentication) {
        rateLimiter.acquire(userId, isAdmin(authentication), MapSearchOperation.ROUTE);
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

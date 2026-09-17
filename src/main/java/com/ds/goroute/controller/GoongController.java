package com.ds.goroute.controller;

import com.ds.goroute.thirdparty.goong.GoongClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxies Goong Maps APIs so the Goong key stays server-side. Requires an
 * authenticated app user (default security rule), which also prevents the
 * proxy itself from being abused to drain the Goong quota.
 */
@RestController
@RequestMapping("/v1/api/goong")
@RequiredArgsConstructor
public class GoongController {

    private final GoongClient goongClient;

    @GetMapping("/autocomplete")
    public ResponseEntity<String> autocomplete(
            @RequestParam String input,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sessiontoken,
            @RequestParam(required = false) Boolean more_compound) {
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
            @RequestParam(required = false) String sessiontoken) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("place_id", placeId);
        if (sessiontoken != null) params.add("sessiontoken", sessiontoken);
        return goongClient.forward("/Place/Detail", params);
    }

    @GetMapping("/geocode")
    public ResponseEntity<String> geocode(
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String latlng) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (address != null) params.add("address", address);
        if (latlng != null) params.add("latlng", latlng);
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
}

package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.NetworkLocationResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's rough location from their network, for the app to fall back on when the
 * device gives no position (location off, not allowed, no fix).
 *
 * <p>Read from Cloudflare's visitor location headers ({@code cf-iplatitude},
 * {@code cf-iplongitude}, {@code cf-ipcity}, {@code cf-ipcountry}), which the zone adds when
 * the "Add visitor location headers" managed transform is on. Nothing is looked up or
 * stored here. Without those headers (the transform off, a request that bypassed
 * Cloudflare, local development) the answer is {@code data: null}, and the app moves on to
 * its next fallback.
 */
@RestController
@RequestMapping("/v1/api/geo")
public class NetworkLocationController extends BaseController {

    @GetMapping("/network-location")
    public ResponseEntity<BaseResponse<NetworkLocationResponse>> networkLocation(
            @RequestHeader(value = "cf-iplatitude", required = false) String latitude,
            @RequestHeader(value = "cf-iplongitude", required = false) String longitude,
            @RequestHeader(value = "cf-ipcity", required = false) String city,
            @RequestHeader(value = "cf-ipcountry", required = false) String country) {
        Double lat = parseCoordinate(latitude, 90);
        Double lng = parseCoordinate(longitude, 180);
        NetworkLocationResponse body = lat == null || lng == null
                ? null
                : NetworkLocationResponse.builder()
                        .latitude(lat)
                        .longitude(lng)
                        .city(blankToNull(city))
                        // "XX" is Cloudflare's "unknown"; "T1" is Tor.
                        .countryCode(country == null || country.length() != 2 || "XX".equals(country)
                                ? null
                                : country)
                        .build();
        // One person's answer, and it changes with their network: never cached.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ofSucceeded(body));
    }

    private static Double parseCoordinate(String value, double limit) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) && Math.abs(parsed) <= limit ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

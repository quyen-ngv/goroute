package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.RouteDayRequest;
import com.ds.goroute.dto.response.RouteDayResponse;
import com.ds.goroute.service.MapSearchRateLimiter;
import com.ds.goroute.service.mapsearch.RouteService;
import com.ds.goroute.type.MapSearchOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.ds.goroute.dto.BaseResponse.ofSucceeded;

/**
 * Road routes for the app's maps and timeline. Replaces the app's own calls to Mapbox
 * Directions and Google Distance Matrix: one request per day of stops, cached per leg for
 * everyone, rate limited per user (MAP_SEARCH/ROUTE_*) and inside the system map budget.
 */
@RestController
@RequestMapping("/v1/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final MapSearchRateLimiter rateLimiter;

    @PostMapping("/day")
    public ResponseEntity<BaseResponse<RouteDayResponse>> day(@Valid @RequestBody RouteDayRequest request,
                                                              @CurrentUser UUID userId,
                                                              Authentication authentication) {
        rateLimiter.acquire(userId, isAdmin(authentication), MapSearchOperation.ROUTE);
        return ResponseEntity.ok(ofSucceeded(routeService.day(request)));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}

package com.ds.goroute.controller;

import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.TripVoteResponse;
import com.ds.goroute.service.TripService;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.BaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
public class PublicTripController extends BaseService {
    
    private final TripService tripService;

    public PublicTripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping("/v1/api/public/trips/{tripId}")
    public ResponseEntity<BaseResponse<PublicTripResponse>> getPublicTrip(
            @PathVariable UUID tripId,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        PublicTripResponse trip = tripService.getPublicTrip(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(trip));
    }

    @PostMapping("/v1/api/public/trips/{tripId}/helpful")
    public ResponseEntity<BaseResponse<TripVoteResponse>> voteTripHelpful(
            @PathVariable UUID tripId,
            @RequestAttribute("userId") UUID userId) {
        TripVoteResponse result = tripService.voteTripHelpful(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(result));
    }

    @PostMapping("/v1/api/public/trips/{tripId}/unhelpful")
    public ResponseEntity<BaseResponse<TripVoteResponse>> voteTripUnhelpful(
            @PathVariable UUID tripId,
            @RequestAttribute("userId") UUID userId) {
        TripVoteResponse result = tripService.voteTripUnhelpful(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(result));
    }
    
    @GetMapping("/v1/api/public/trips/search")
    public ResponseEntity<BaseResponse<List<PublicTripResponse>>> searchPublicTrips(
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude,
            @RequestParam(defaultValue = "50") BigDecimal radiusKm,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean allPublic,
            @RequestParam(defaultValue = "true") boolean excludeUserTrips,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) UUID userId
    ) {
        UUID excludedUserId = excludeUserTrips ? userId : null;
        List<PublicTripResponse> trips = tripService.searchPublicTrips(
                latitude,
                longitude,
                radiusKm,
                destination,
                keyword,
                allPublic,
                page,
                size,
                excludedUserId
        );
        return ResponseEntity.ok(ofSucceeded(trips));
    }
    
    @GetMapping(value = "/share/{tripId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSharePage(@PathVariable String tripId) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/share-trip.html");
        String html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(html);
    }
}

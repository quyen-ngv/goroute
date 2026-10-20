package com.ds.goroute.controller;

import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.TripVoteResponse;
import com.ds.goroute.service.TripService;
import com.ds.goroute.dto.BaseResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@Validated
public class PublicTripController extends BaseController {

    /** Every card costs a batch of lookups; the app asks for 20. */
    static final int MAX_SEARCH_PAGE_SIZE = 50;
    
    private final TripService tripService;

    public PublicTripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping("/v1/api/public/trips/{tripId}")
    public ResponseEntity<BaseResponse<PublicTripResponse>> getPublicTrip(
            @PathVariable UUID tripId,
            @CurrentUser(required = false) UUID userId) {
        PublicTripResponse trip = tripService.getPublicTrip(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(trip));
    }

    @PostMapping("/v1/api/public/trips/{tripId}/helpful")
    public ResponseEntity<BaseResponse<TripVoteResponse>> voteTripHelpful(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        TripVoteResponse result = tripService.voteTripHelpful(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(result));
    }

    @PostMapping("/v1/api/public/trips/{tripId}/unhelpful")
    public ResponseEntity<BaseResponse<TripVoteResponse>> voteTripUnhelpful(
            @PathVariable UUID tripId,
            @CurrentUser UUID userId) {
        TripVoteResponse result = tripService.voteTripUnhelpful(tripId, userId);
        return ResponseEntity.ok(ofSucceeded(result));
    }
    
    @GetMapping("/v1/api/public/trips/search")
    public ResponseEntity<BaseResponse<List<PublicTripResponse>>> searchPublicTrips(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            // Half the Earth's circumference: a larger radius means nothing.
            @RequestParam(defaultValue = "50") @DecimalMin("0") @DecimalMax("20040") BigDecimal radiusKm,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean allPublic,
            @RequestParam(defaultValue = "true") boolean excludeUserTrips,
            @RequestParam(required = false) String randomSeed,
            // Trips with a cover or memory photos first; the feed asks for this.
            @RequestParam(defaultValue = "false") boolean preferImages,
            // The Feed's ranking: trips with many stops and photos, recent ones first.
            // Replaces randomSeed and distance ordering when set.
            @RequestParam(defaultValue = "false") boolean recommended,
            // The Feed's place-kind chips: PlaceGroup and PlaceSubType codes. A post matches
            // through its catalogue place, a trip through any of its stops.
            @RequestParam(required = false) List<String> placeGroups,
            @RequestParam(required = false) List<String> subTypes,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_SEARCH_PAGE_SIZE) int size,
            @CurrentUser(required = false) UUID userId
    ) {
        UUID excludedUserId = excludeUserTrips ? userId : null;
        List<PublicTripResponse> trips = tripService.searchPublicTrips(
                latitude,
                longitude,
                radiusKm,
                destination,
                keyword,
                allPublic,
                randomSeed,
                preferImages,
                recommended,
                FeedPlaceKind.of(placeGroups, subTypes),
                page,
                size,
                userId,
                excludedUserId
        );
        return ResponseEntity.ok(ofSucceeded(trips));
    }
    
    /**
     * Public itineraries that include a catalogue place, most recently shared first, for
     * Place Detail. Only public trips: the place's tripUseCount also counts private ones.
     */
    @GetMapping("/v1/api/places/{placeId}/public-trips")
    public ResponseEntity<BaseResponse<List<PublicTripResponse>>> getPublicTripsWithPlace(
            @PathVariable UUID placeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_SEARCH_PAGE_SIZE) int size,
            @CurrentUser(required = false) UUID userId) {
        return ResponseEntity.ok(ofSucceeded(
                tripService.getPublicTripsWithPlace(placeId, page, size, userId)));
    }

    /**
     * The Ondetour web page. One file backs both the public-trip list and a
     * single trip; the browser decides which to render from the path, so every
     * route here returns the same HTML. /share/{tripId} is the legacy link
     * shape and stays mapped so links already shared keep resolving.
     */
    @GetMapping(value = {"/share/{tripId}", "/ondetour", "/ondetour/{tripId}"},
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSharePage() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/ondetour.html");
        String html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(html);
    }
}

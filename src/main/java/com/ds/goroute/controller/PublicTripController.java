package com.ds.goroute.controller;

import com.ds.goroute.dto.PublicTripSearchCriteria;
import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.TripVoteResponse;
import com.ds.goroute.service.PublicPageService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.dto.BaseResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@Validated
public class PublicTripController extends BaseController {

    /** Every card costs a batch of lookups; the app asks for 20. */
    static final int MAX_SEARCH_PAGE_SIZE = 50;
    
    private final TripService tripService;
    private final PublicPageService publicPageService;

    public PublicTripController(TripService tripService, PublicPageService publicPageService) {
        this.tripService = tripService;
        this.publicPageService = publicPageService;
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
            // Only trips with memory photos (the Feed's "All" chip).
            @RequestParam(defaultValue = "false") boolean photosOnly,
            // Trips with memory photos first, then the most stops (the Feed's "Trips" chip).
            // Replaces every other order when set.
            @RequestParam(defaultValue = "false") boolean photosThenStops,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_SEARCH_PAGE_SIZE) int size,
            @CurrentUser(required = false) UUID userId
    ) {
        UUID excludedUserId = excludeUserTrips ? userId : null;
        PublicTripSearchCriteria criteria = PublicTripSearchCriteria.of(
                latitude,
                longitude,
                radiusKm,
                destination,
                keyword,
                allPublic,
                randomSeed,
                preferImages,
                recommended,
                FeedPlaceKind.of(placeGroups, subTypes)
        ).withPhotoRules(photosOnly, photosThenStops);
        List<PublicTripResponse> trips = tripService.searchPublicTrips(
                criteria,
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
     * shape and stays mapped so links already shared keep resolving. A public
     * trip's link carries its own preview tags (title, cover) for Zalo,
     * Messenger and search engines; the page still loads the trip itself.
     */
    @GetMapping(value = {"/share/{tripId}", "/ondetour", "/ondetour/{tripId}"},
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSharePage(@PathVariable(required = false) String tripId) {
        return ResponseEntity.ok(publicPageService.tripPage(parseTripId(tripId)).html());
    }

    /** Anything that is not a UUID is the list view, as the page's own script decides. */
    private static UUID parseTripId(String tripId) {
        if (tripId == null || tripId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(tripId.replaceFirst("\\.html$", ""));
        } catch (IllegalArgumentException notATripId) {
            return null;
        }
    }
}

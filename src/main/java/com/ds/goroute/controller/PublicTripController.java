package com.ds.goroute.controller;

import com.ds.goroute.dto.response.PublicTripResponse;
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
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PublicTripController extends BaseService {
    
    private final TripService tripService;

    @GetMapping("/v1/api/public/trips/{tripId}")
    public ResponseEntity<BaseResponse<PublicTripResponse>> getPublicTrip(@PathVariable UUID tripId) {
        PublicTripResponse trip = tripService.getPublicTrip(tripId);
        return ResponseEntity.ok(ofSucceeded(trip));
    }
    
    @GetMapping(value = "/share/{tripId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSharePage(@PathVariable String tripId) throws IOException {
        log.info("Serving share page for trip: {}", tripId);
        ClassPathResource resource = new ClassPathResource("static/share-trip.html");
        String html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(html);
    }
}

package com.ds.goroute.localevent.web;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.localevent.dto.LocalEventDetailResponse;
import com.ds.goroute.localevent.dto.LocalEventSummaryResponse;
import com.ds.goroute.localevent.service.LocalEventPublicService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public local events for the app's Home "Sự kiện" tab (epic 17). Under /v1/api/public/**, so
 * reachable without a token. Only PUBLISHED, live, mappable events are returned.
 */
@Validated
@RestController
@RequestMapping("/v1/api/public/events")
@RequiredArgsConstructor
public class LocalEventPublicController {

    private final LocalEventPublicService publicService;

    /**
     * Events within {@code radiusKm} of a point whose span overlaps [from, to] (ISO-8601 instants
     * with offset; default now .. now + 60 days, at most 366 days). {@code category} is a comma list.
     * Ordered by start time.
     */
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<LocalEventSummaryResponse>>> search(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
            @RequestParam(defaultValue = "30") @DecimalMin("1") @DecimalMax("100") double radiusKm,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                publicService.search(lat, lng, radiusKm, from, to, category, page, size)));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<BaseResponse<LocalEventDetailResponse>> detail(@PathVariable UUID eventId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(publicService.detail(eventId)));
    }
}

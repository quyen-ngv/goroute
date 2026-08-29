package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PassportSummaryResponse;
import com.ds.goroute.dto.response.ProvinceMapEntryResponse;
import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.PassportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Passport (epic 04): what somebody has seen, and what they earned for it. */
@RestController
@RequestMapping("/v1/api/passport")
@RequiredArgsConstructor
@Validated
public class PassportController extends BaseService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PassportService passportService;

    @GetMapping
    public ResponseEntity<BaseResponse<PassportSummaryResponse>> summary(
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(passportService.summary(userId)));
    }

    /** All 63 provinces with their state, computed on the server so every screen agrees. */
    @GetMapping("/map")
    public ResponseEntity<BaseResponse<List<ProvinceMapEntryResponse>>> map(
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(passportService.provinceMap(userId)));
    }

    @GetMapping("/timeline")
    public ResponseEntity<BaseResponse<List<PassportEvent>>> timeline(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(passportService.timeline(userId, page, size)));
    }

    @GetMapping("/provinces/{provinceCode}")
    public ResponseEntity<BaseResponse<List<PassportEvent>>> province(
            @PathVariable @Size(max = 10) String provinceCode,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int limit,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(passportService.province(userId, provinceCode, limit)));
    }

    @PostMapping("/events/{eventId}/hide")
    public ResponseEntity<BaseResponse<Void>> hide(@PathVariable UUID eventId,
                                                   @RequestAttribute("userId") UUID userId) {
        passportService.setEventHidden(userId, eventId, true);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/events/{eventId}/unhide")
    public ResponseEntity<BaseResponse<Void>> unhide(@PathVariable UUID eventId,
                                                     @RequestAttribute("userId") UUID userId) {
        passportService.setEventHidden(userId, eventId, false);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** Wanting to go somewhere never counts as having been. */
    @PostMapping("/wishes/{provinceCode}")
    public ResponseEntity<BaseResponse<Void>> addWish(@PathVariable @Size(max = 10) String provinceCode,
                                                      @RequestAttribute("userId") UUID userId) {
        passportService.addProvinceWish(userId, provinceCode);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/wishes/{provinceCode}")
    public ResponseEntity<BaseResponse<Void>> removeWish(@PathVariable @Size(max = 10) String provinceCode,
                                                         @RequestAttribute("userId") UUID userId) {
        passportService.removeProvinceWish(userId, provinceCode);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}

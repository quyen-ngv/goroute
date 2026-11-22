package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.StartImageCompressionRunRequest;
import com.ds.goroute.dto.response.ImageCompressionOverviewResponse;
import com.ds.goroute.dto.response.ImageCompressionRunResponse;
import com.ds.goroute.dto.response.ImageCompressionSampleResponse;
import com.ds.goroute.service.imagecompression.ImageCompressionAdminService;
import com.ds.goroute.service.imagecompression.ImageCompressionRunner;
import com.ds.goroute.type.ImageCompressionGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Console image compression across every storage group (epic 00 §9b). Compression overwrites
 * files under their own keys and cannot be undone; samples and previews write nothing. The
 * settings themselves are ordinary {@code IMAGE_COMPRESSION.*} rows edited through
 * {@code /v1/api/admin/configs}.
 */
@RestController
@RequestMapping("/v1/api/admin/image-compression")
@RequiredArgsConstructor
@Validated
public class AdminImageCompressionController extends BaseController {

    private final ImageCompressionAdminService adminService;
    private final ImageCompressionRunner runner;

    @GetMapping("/groups")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','get')")
    public ResponseEntity<BaseResponse<ImageCompressionOverviewResponse>> overview() {
        return ResponseEntity.ok(ofSucceeded(adminService.overview()));
    }

    @GetMapping("/groups/{group}/samples")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','get')")
    public ResponseEntity<BaseResponse<List<ImageCompressionSampleResponse>>> samples(
            @PathVariable ImageCompressionGroup group,
            @RequestParam(defaultValue = "12") @Min(1) @Max(ImageCompressionAdminService.MAX_SAMPLES) int limit) {
        return ResponseEntity.ok(ofSucceeded(adminService.samples(group, limit)));
    }

    /** One stored image compressed in memory, as WebP; headers X-Original-Bytes, X-Tier, X-Quality, X-Max-Long-Edge. */
    @GetMapping(value = "/preview", produces = "image/webp")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','get')")
    public ResponseEntity<byte[]> preview(
            @RequestParam @NotBlank String key,
            @RequestParam(required = false) @Min(30) @Max(95) Integer quality,
            @RequestParam(required = false) @Min(640) @Max(4096) Integer maxLongEdge) {
        ImageCompressionAdminService.Preview preview = adminService.preview(key, quality, maxLongEdge);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .cacheControl(CacheControl.noStore())
                .header("X-Original-Bytes", String.valueOf(preview.originalBytes()))
                .header("X-Tier", preview.tier().name())
                .header("X-Quality", String.valueOf(preview.quality()))
                .header("X-Max-Long-Edge", String.valueOf(preview.maxLongEdge()))
                .body(preview.webp());
    }

    @GetMapping("/runs/current")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','get')")
    public ResponseEntity<BaseResponse<ImageCompressionRunResponse>> currentRun() {
        return ResponseEntity.ok(ofSucceeded(runner.current()));
    }

    @PostMapping("/runs")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<ImageCompressionRunResponse>> startRun(
            @Valid @RequestBody(required = false) StartImageCompressionRunRequest request) {
        ImageCompressionRunResponse run = runner.start(request == null ? null : request.getGroups());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ofSucceeded(run));
    }

    @PostMapping("/runs/current/cancel")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<ImageCompressionRunResponse>> cancelRun() {
        return ResponseEntity.ok(ofSucceeded(runner.cancel()));
    }
}

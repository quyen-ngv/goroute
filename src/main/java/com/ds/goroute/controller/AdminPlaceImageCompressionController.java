package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CompressPlaceImagesRequest;
import com.ds.goroute.dto.response.PlaceImageCompressionResponse;
import com.ds.goroute.service.PlaceImageCompressionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
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

import java.util.UUID;

/**
 * Console place image compression with caesium (epic 06 §2.13). Compression overwrites the
 * files under their own keys and cannot be undone; the dry run and the preview write nothing.
 */
@RestController
@RequestMapping("/v1/api/admin")
@RequiredArgsConstructor
@Validated
public class AdminPlaceImageCompressionController extends BaseController {

    private final PlaceImageCompressionService compressionService;

    @GetMapping("/places/{placeId}/image-compression")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity<BaseResponse<PlaceImageCompressionResponse>> getPlaceCompression(@PathVariable UUID placeId) {
        return ResponseEntity.ok(ofSucceeded(compressionService.getState(placeId)));
    }

    @PostMapping("/places/{placeId}/image-compression")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity<BaseResponse<PlaceImageCompressionResponse>> compressPlace(
            @PathVariable UUID placeId,
            @Valid @RequestBody(required = false) CompressPlaceImagesRequest request) {
        return ResponseEntity.ok(ofSucceeded(compressionService.compress(placeId, request)));
    }

    /** One of the place's images compressed in memory, as WebP; header X-Original-Bytes. */
    @GetMapping(value = "/places/{placeId}/image-compression/preview", produces = "image/webp")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity<byte[]> previewPlaceImage(
            @PathVariable UUID placeId,
            @RequestParam @NotBlank String url,
            @RequestParam(required = false) @Min(30) @Max(95) Integer quality,
            @RequestParam(required = false) @Min(640) @Max(4096) Integer maxLongEdge) {
        PlaceImageCompressionService.Preview preview = compressionService.preview(placeId, url, quality, maxLongEdge);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .cacheControl(CacheControl.noStore())
                .header("X-Original-Bytes", String.valueOf(preview.originalBytes()))
                .body(preview.webp());
    }
}

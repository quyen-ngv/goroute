package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.GeoDatasetResponse;
import com.ds.goroute.dto.response.GeoProvinceResponse;
import com.ds.goroute.dto.response.GeoWardResponse;
import com.ds.goroute.service.GeoService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Administrative boundaries for the app. Public because the shapes are public data,
 * and cacheable for a day because they change once per government decree.
 */
@RestController
@RequestMapping("/v1/api/public/geo")
@RequiredArgsConstructor
@Validated
public class PublicGeoController extends BaseController {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");
    private static final String CODE_PATTERN = "^[0-9]{2,5}$";

    private final GeoService geoService;

    @GetMapping("/dataset")
    public ResponseEntity<BaseResponse<GeoDatasetResponse>> dataset() {
        return ResponseEntity.ok(ofSucceeded(geoService.dataset()));
    }

    @GetMapping("/provinces")
    public ResponseEntity<BaseResponse<List<GeoProvinceResponse>>> provinces() {
        return ResponseEntity.ok(ofSucceeded(geoService.provinces()));
    }

    @GetMapping("/provinces/{code}/wards")
    public ResponseEntity<BaseResponse<List<GeoWardResponse>>> wards(
            @PathVariable @Pattern(regexp = CODE_PATTERN) String code) {
        return ResponseEntity.ok(ofSucceeded(geoService.wardsOf(code)));
    }

    @GetMapping("/wards/{code}")
    public ResponseEntity<BaseResponse<GeoWardResponse>> ward(
            @PathVariable @Pattern(regexp = CODE_PATTERN) String code) {
        return geoService.findWard(code)
                .map(ward -> ResponseEntity.ok(ofSucceeded(GeoWardResponse.from(ward))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * The official ward a coordinate falls in. 404 outside every boundary, or before the
     * dataset is loaded.
     *
     * <p>This is what turns a geocoder's guess into an administrative fact: the admin
     * console fills an address from Goong, then asks here which ward that point is really
     * in rather than trusting the words the geocoder returned.
     */
    @GetMapping("/wards/at")
    public ResponseEntity<BaseResponse<GeoWardResponse>> wardAt(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {
        return geoService.resolveWard(latitude, longitude)
                .map(ward -> ResponseEntity.ok(ofSucceeded(GeoWardResponse.from(ward))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Raw FeatureCollection, not the BaseResponse envelope: Mapbox consumes it as-is. */
    @GetMapping(value = "/provinces.geojson", produces = "application/geo+json")
    public ResponseEntity<String> provincesGeoJson(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return geoJson(geoService.provincesGeoJson(), "provinces", ifNoneMatch);
    }

    @GetMapping(value = "/provinces/{code}/wards.geojson", produces = "application/geo+json")
    public ResponseEntity<String> wardsGeoJson(
            @PathVariable @Pattern(regexp = CODE_PATTERN) String code,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return geoJson(geoService.wardsGeoJson(code), "wards-" + code, ifNoneMatch);
    }

    private ResponseEntity<String> geoJson(Optional<String> body, String layer, String ifNoneMatch) {
        if (body.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // The dataset version is the only thing that can change a layer, so it is the tag.
        String version = geoService.datasetVersion().orElse("none");
        String etag = "\"" + layer + "-" + version + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(body.get());
    }
}

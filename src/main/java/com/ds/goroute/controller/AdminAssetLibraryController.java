package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateLibraryAssetRequest;
import com.ds.goroute.dto.request.UpdateLibraryAssetRequest;
import com.ds.goroute.dto.response.LibraryAssetResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.LibraryAssetService;
import com.ds.goroute.type.AssetCollection;
import com.ds.goroute.type.AssetStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * The shared image library (epic 06 §2.5b): ingredient icons now, banners and promotions later.
 * Records reference an asset by id, so replacing an image here updates every record using it.
 */
@RestController
@RequestMapping("/v1/api/admin/asset-library")
@RequiredArgsConstructor
@Validated
public class AdminAssetLibraryController extends BaseController {

    /**
     * Above the usual 100 on purpose: the food editor's ingredient picker loads the whole
     * collection once (a catalogue of a few hundred icons), within the global 500 cap.
     */
    private static final int MAX_PAGE_SIZE = 500;

    private final LibraryAssetService libraryAssetService;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'assets','get')")
    public ResponseEntity<BaseResponse<PageResponse<LibraryAssetResponse>>> search(
            @RequestParam(required = false) AssetCollection collection,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "false") boolean missingImage,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(
                libraryAssetService.adminSearch(collection, status, q, missingImage, page, size)));
    }

    @GetMapping("/{assetId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'assets','get')")
    public ResponseEntity<BaseResponse<LibraryAssetResponse>> get(@PathVariable UUID assetId) {
        return ResponseEntity.ok(ofSucceeded(libraryAssetService.adminGet(assetId)));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'assets','create')")
    public ResponseEntity<BaseResponse<LibraryAssetResponse>> create(
            @Valid @RequestBody CreateLibraryAssetRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(libraryAssetService.adminCreate(request, adminId)));
    }

    @PutMapping("/{assetId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'assets','update')")
    public ResponseEntity<BaseResponse<LibraryAssetResponse>> update(
            @PathVariable UUID assetId,
            @Valid @RequestBody UpdateLibraryAssetRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(libraryAssetService.adminUpdate(assetId, request, adminId)));
    }

    /** Replaces the image; every record that references the asset shows the new one. */
    @PostMapping(value = "/{assetId}/image", consumes = "multipart/form-data")
    @PreAuthorize("@adminAuthorization.can(authentication,'assets','update')")
    public ResponseEntity<BaseResponse<LibraryAssetResponse>> replaceImage(
            @PathVariable UUID assetId,
            @RequestPart("file") MultipartFile file,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(libraryAssetService.adminReplaceImage(assetId, file, adminId)));
    }

    @DeleteMapping("/{assetId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'assets','delete')")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID assetId) {
        libraryAssetService.adminDelete(assetId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}

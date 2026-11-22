package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateCameraLookRequest;
import com.ds.goroute.dto.request.ReorderCameraLooksRequest;
import com.ds.goroute.dto.request.UpdateCameraLookRequest;
import com.ds.goroute.dto.response.CameraLookResponse;
import com.ds.goroute.dto.response.CameraLookUploadResponse;
import com.ds.goroute.service.CameraLookService;
import com.ds.goroute.type.CameraLookKind;
import com.ds.goroute.type.CameraLookStatus;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

/**
 * Photo filters and frames of the check-in camera (epic 08, V293). The app bundles a default
 * set; what is managed here is added on top, and a look whose key equals a bundled one replaces it.
 */
@RestController
@RequestMapping("/v1/api/admin/camera-looks")
@RequiredArgsConstructor
@Validated
public class AdminCameraLookController extends BaseController {

    private final CameraLookService cameraLookService;

    /** Not paged: the catalogue is curated by hand and the service caps the read at 500 rows. */
    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'camera-looks','get')")
    public ResponseEntity<BaseResponse<List<CameraLookResponse>>> list(
            @RequestParam(required = false) CameraLookKind kind,
            @RequestParam(required = false) CameraLookStatus status,
            @RequestParam(required = false) @Size(max = 100) String q) {
        return ResponseEntity.ok(ofSucceeded(cameraLookService.adminList(kind, status, q)));
    }

    @GetMapping("/{lookId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'camera-looks','get')")
    public ResponseEntity<BaseResponse<CameraLookResponse>> get(@PathVariable UUID lookId) {
        return ResponseEntity.ok(ofSucceeded(cameraLookService.adminGet(lookId)));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'camera-looks','create')")
    public ResponseEntity<BaseResponse<CameraLookResponse>> create(
            @Valid @RequestBody CreateCameraLookRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(cameraLookService.adminCreate(request, adminId)));
    }

    /** Declared before {@code /{lookId}} for the reader; Spring prefers the literal path either way. */
    @PutMapping("/order")
    @PreAuthorize("@adminAuthorization.can(authentication,'camera-looks','update')")
    public ResponseEntity<BaseResponse<List<CameraLookResponse>>> reorder(
            @Valid @RequestBody ReorderCameraLooksRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(cameraLookService.adminReorder(request, adminId)));
    }

    @PutMapping("/{lookId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'camera-looks','update')")
    public ResponseEntity<BaseResponse<CameraLookResponse>> update(
            @PathVariable UUID lookId,
            @Valid @RequestBody UpdateCameraLookRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(cameraLookService.adminUpdate(lookId, request, adminId)));
    }

    /** A LUT, frame or thumbnail PNG, stored as sent; the URL goes into a look's definition. */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("@adminAuthorization.can(authentication,'camera-looks','create') "
            + "or @adminAuthorization.can(authentication,'camera-looks','update')")
    public ResponseEntity<BaseResponse<CameraLookUploadResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(cameraLookService.adminUpload(file, adminId)));
    }

    @DeleteMapping("/{lookId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'camera-looks','delete')")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID lookId) {
        cameraLookService.adminDelete(lookId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}

package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.CameraLookManifestResponse;
import com.ds.goroute.service.CameraLookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The camera filters and frames the app adds to its bundled set (V293): ACTIVE looks inside
 * their date window, in the shape of the app's bundled manifest. Who may use them is answered by
 * the check-in context ({@code cameraLooksAllowed}), not here.
 */
@RestController
@RequestMapping("/v1/api/public/camera-looks")
@RequiredArgsConstructor
public class PublicCameraLookController extends BaseController {

    private final CameraLookService cameraLookService;

    @GetMapping
    public ResponseEntity<BaseResponse<CameraLookManifestResponse>> manifest() {
        return ResponseEntity.ok(ofSucceeded(cameraLookService.publicManifest()));
    }
}

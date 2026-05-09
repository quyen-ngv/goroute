package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateLocationImageRequest;
import com.ds.goroute.dto.request.UpdateLocationImageRequest;
import com.ds.goroute.dto.response.LocationImageResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.LocationImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/location-images")
@RequiredArgsConstructor
@Slf4j
public class LocationImageController extends BaseService {
    
    private final LocationImageService locationImageService;
    
    @GetMapping
    public ResponseEntity<BaseResponse<List<LocationImageResponse>>> getAllLocationImages() {
        List<LocationImageResponse> locationImages = locationImageService.getAllLocationImages();
        return ResponseEntity.ok(ofSucceeded(locationImages));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<LocationImageResponse>> getLocationImage(@PathVariable UUID id) {
        LocationImageResponse locationImage = locationImageService.getLocationImage(id);
        return ResponseEntity.ok(ofSucceeded(locationImage));
    }
    
    @PostMapping
    public ResponseEntity<BaseResponse<LocationImageResponse>> createLocationImage(
            @Valid @RequestBody CreateLocationImageRequest request) {
        LocationImageResponse locationImage = locationImageService.createLocationImage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(locationImage));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<LocationImageResponse>> updateLocationImage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationImageRequest request) {
        LocationImageResponse locationImage = locationImageService.updateLocationImage(id, request);
        return ResponseEntity.ok(ofSucceeded(locationImage));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteLocationImage(@PathVariable UUID id) {
        locationImageService.deleteLocationImage(id);
        return ResponseEntity.ok(ofSucceeded(null));
    }
    
    @PostMapping("/upload")
    public ResponseEntity<BaseResponse<String>> uploadLocationImage(
            @RequestParam("file") MultipartFile file) {
        String imageUrl = locationImageService.uploadLocationImage(file);
        return ResponseEntity.ok(ofSucceeded(imageUrl));
    }
    
    @GetMapping("/search")
    public ResponseEntity<BaseResponse<String>> searchImageByDestination(
            @RequestParam String destination) {
        String imageUrl = locationImageService.getImageForDestination(destination);
        return ResponseEntity.ok(ofSucceeded(imageUrl));
    }
}

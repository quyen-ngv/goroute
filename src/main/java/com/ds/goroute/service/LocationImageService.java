package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateLocationImageRequest;
import com.ds.goroute.dto.request.UpdateLocationImageRequest;
import com.ds.goroute.dto.response.LocationImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface LocationImageService {
    String getImageForDestination(String destination);
    List<LocationImageResponse> getAllLocationImages();
    LocationImageResponse getLocationImage(UUID id);
    LocationImageResponse createLocationImage(CreateLocationImageRequest request);
    LocationImageResponse updateLocationImage(UUID id, UpdateLocationImageRequest request);
    void deleteLocationImage(UUID id);
    String uploadLocationImage(MultipartFile file);
}

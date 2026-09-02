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

    /**
     * @param includeWeather attach live weather and air quality to every entry; entries
     *                       without coordinates, and every entry during a provider
     *                       outage, simply come back with a {@code null} weather block
     */
    List<LocationImageResponse> getAllLocationImages(boolean includeWeather);

    LocationImageResponse getLocationImage(UUID id);

    /** @param includeWeather attach live weather and air quality; {@code null} when unavailable */
    LocationImageResponse getLocationImage(UUID id, boolean includeWeather);

    LocationImageResponse createLocationImage(CreateLocationImageRequest request);

    LocationImageResponse updateLocationImage(UUID id, UpdateLocationImageRequest request);

    void deleteLocationImage(UUID id);

    String uploadLocationImage(MultipartFile file);

    /** Video upload for city stories; unmoderated, same as other operator video uploads. */
    String uploadLocationVideo(MultipartFile file);
}

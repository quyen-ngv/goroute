package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateLocationImageRequest;
import com.ds.goroute.dto.request.UpdateLocationImageRequest;
import com.ds.goroute.dto.response.LocationImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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

    /**
     * The "Bán kính phủ" of the tourist area a trip stop belongs to: the area already linked by
     * {@code locationImageId} when it still exists, otherwise the area resolved from the
     * coordinates, then from the names in {@code texts} ({@link com.ds.goroute.utils.LocationAreaMatcher}).
     * Empty when no area matches.
     */
    Optional<BigDecimal> findCoverageRadiusKm(UUID locationImageId, BigDecimal latitude, BigDecimal longitude,
                                              List<String> texts);

    LocationImageResponse createLocationImage(CreateLocationImageRequest request);

    LocationImageResponse updateLocationImage(UUID id, UpdateLocationImageRequest request);

    void deleteLocationImage(UUID id);

    String uploadLocationImage(MultipartFile file);

    /** Video upload for city stories; unmoderated, same as other operator video uploads. */
    String uploadLocationVideo(MultipartFile file);
}

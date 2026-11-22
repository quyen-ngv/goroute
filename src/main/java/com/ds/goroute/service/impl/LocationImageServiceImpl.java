package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.LocationDescriptionContent;
import com.ds.goroute.dto.LocationDescriptionSection;
import com.ds.goroute.dto.request.CreateLocationImageRequest;
import com.ds.goroute.dto.request.UpdateLocationImageRequest;
import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.dto.response.LocationImageResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.enums.LocationDescriptionType;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.CityWeatherService;
import com.ds.goroute.service.LocationImageService;
import com.ds.goroute.utils.CitySlugResolver;
import com.ds.goroute.utils.LocationAreaMatcher;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationImageServiceImpl implements LocationImageService {

    private final LocationImageRepository locationImageRepository;
    private final FileUploadService fileUploadService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final ObjectMapper objectMapper;
    private final CityWeatherService cityWeatherService;
    private final BusinessConfigService businessConfigService;

    /** Shared pool; the weather calls are short, cached, and never touch the database. */
    private final Executor applicationTaskExecutor;

    private static final String DEFAULT_IMAGE = "https://images.unsplash.com/photo-1488646953014-85cb44e25828";

    static final Duration CATALOG_CACHE_TTL = Duration.ofMinutes(5);
    static final Duration BEST_MATCH_CACHE_TTL = Duration.ofMinutes(10);
    private static final int BEST_MATCH_CACHE_SIZE = 2_000;

    /**
     * The area list Home and Explore load on every open: rows (without the JSONB description) plus ward
     * links. One entry; dropped on every create/update/delete here (the only writers of areas and their
     * ward links) and again after that transaction commits.
     */
    private final Cache<Boolean, CatalogSnapshot> catalogCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(CATALOG_CACHE_TTL)
            .build();

    /** Best-matching area image per normalized destination; trip cards ask once per card. */
    private final Cache<String, Optional<String>> bestMatchCache = Caffeine.newBuilder()
            .maximumSize(BEST_MATCH_CACHE_SIZE)
            .expireAfterWrite(BEST_MATCH_CACHE_TTL)
            .build();

    private record CatalogSnapshot(List<LocationImage> rows, Map<UUID, List<String>> wardsByArea) {
    }

    @Override
    public String getImageForDestination(String destination) {
        if (destination == null || destination.isEmpty()) {
            return defaultImageUrl();
        }

        String normalized = normalizeVietnamese(destination.toLowerCase());

        // The default is not cached: it is an admin setting with its own cache.
        return bestMatchCache.get(normalized, key -> locationImageRepository.findBestMatch(key)
                .map(LocationImage::getImageUrl))
            .orElseGet(this::defaultImageUrl);
    }

    private String defaultImageUrl() {
        if (businessConfigService == null) {
            return DEFAULT_IMAGE;
        }
        String configured = businessConfigService.getText(BusinessConfigKey.LOCATION_IMAGE_DEFAULT_URL);
        return configured == null || configured.isBlank() ? DEFAULT_IMAGE : configured;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationImageResponse> getAllLocationImages() {
        return getAllLocationImages(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationImageResponse> getAllLocationImages(boolean includeWeather) {
        CatalogSnapshot catalog = catalogCache.get(Boolean.TRUE, ignored -> loadCatalog());
        List<LocationImage> locationImages = catalog.rows();
        Map<UUID, List<String>> wardsByArea = catalog.wardsByArea();
        if (!includeWeather) {
            return locationImages.stream()
                .map(locationImage -> withWards(mapToListResponse(locationImage, null), wardsByArea))
                .collect(Collectors.toList());
        }

        // A cold cache would otherwise cost one sequential upstream round-trip per city.
        // Weather is cached per grid cell, so repeats across the list collapse into one call.
        List<CompletableFuture<LocationImageResponse>> pending = locationImages.stream()
            .map(locationImage -> CompletableFuture.supplyAsync(
                () -> withWards(mapToListResponse(locationImage, cityWeatherService.findWeatherQuietly(locationImage)),
                        wardsByArea),
                applicationTaskExecutor))
            .toList();

        return pending.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public LocationImageResponse getLocationImage(UUID id) {
        return getLocationImage(id, false);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationImageResponse getLocationImage(UUID id, boolean includeWeather) {
        LocationImage locationImage = locationImageRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Location image not found"));
        LocationImageResponse response = includeWeather
            ? mapToResponse(locationImage, cityWeatherService.findWeatherQuietly(locationImage))
            : mapToResponse(locationImage);
        response.setWardCodes(locationImageRepository.findWardCodes(id));
        return response;
    }

    @Override
    public Optional<BigDecimal> findCoverageRadiusKm(UUID locationImageId, BigDecimal latitude, BigDecimal longitude,
                                                     List<String> texts) {
        List<LocationImage> areas = catalogCache.get(Boolean.TRUE, ignored -> loadCatalog()).rows();
        Optional<LocationImage> linked = locationImageId == null
                ? Optional.empty()
                : areas.stream().filter(area -> locationImageId.equals(area.getId())).findFirst();
        return linked.or(() -> LocationAreaMatcher.match(areas, latitude, longitude, texts))
                .map(LocationImage::getCoverageRadiusKm);
    }

    private CatalogSnapshot loadCatalog() {
        // One query for every area's wards rather than one per row.
        Map<UUID, List<String>> wardsByArea = new java.util.HashMap<>();
        locationImageRepository.findAllWardCodes()
                .forEach((areaId, wards) -> wardsByArea.put(areaId, List.copyOf(wards)));
        return new CatalogSnapshot(List.copyOf(locationImageRepository.findAllForList()), Map.copyOf(wardsByArea));
    }

    /**
     * Drops the cached area list and destination matches now and once more after the surrounding
     * transaction commits, so a read racing the write cannot re-cache the old rows.
     */
    private void invalidateCaches() {
        catalogCache.invalidateAll();
        bestMatchCache.invalidateAll();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    catalogCache.invalidateAll();
                    bestMatchCache.invalidateAll();
                }
            });
        }
    }

    private LocationImageResponse withWards(LocationImageResponse response, Map<UUID, List<String>> wardsByArea) {
        response.setWardCodes(wardsByArea.getOrDefault(response.getId(), List.of()));
        return response;
    }

    @Override
    @Transactional
    public LocationImageResponse createLocationImage(CreateLocationImageRequest request) {
        LocationImage locationImage = LocationImage.builder()
            .id(UUID.randomUUID())
            .fullAddress(request.getFullAddress())
            .slogan(request.getSlogan())
            .description(serializeDescription(normalizeDescription(request.getDescription())))
            .imageUrl(request.getImageUrl())
            .avatarUrl(resolveAvatarUrl(request.getAvatarUrl(), request.getImageUrl()))
            .provinceCode(blankToNull(request.getProvinceCode()))
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .priority(request.getPriority())
            .coverageRadiusKm(request.getCoverageRadiusKm())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        locationImage.normalizeAddress();
        CitySlugResolver.resolveFromDestination(request.getFullAddress())
                .ifPresent(city -> locationImage.setCitySlug(city.getSlug()));
        locationImageRepository.insert(locationImage);
        if (request.getWardCodes() != null) {
            locationImageRepository.replaceWardCodes(locationImage.getId(), request.getWardCodes());
        }
        invalidateCaches();

        log.info("Location image created: {}", locationImage.getId());
        LocationImageResponse response = mapToResponse(locationImage);
        response.setWardCodes(locationImageRepository.findWardCodes(locationImage.getId()));
        return response;
    }

    @Override
    @Transactional
    public LocationImageResponse updateLocationImage(UUID id, UpdateLocationImageRequest request) {
        LocationImage locationImage = locationImageRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Location image not found"));

        if (request.getFullAddress() != null) {
            locationImage.setFullAddress(request.getFullAddress());
        }
        if (request.getSlogan() != null) {
            locationImage.setSlogan(request.getSlogan());
        }
        if (request.getDescription() != null) {
            locationImage.setDescription(serializeDescription(normalizeDescription(request.getDescription())));
        }
        if (request.getImageUrl() != null) {
            locationImage.setImageUrl(request.getImageUrl());
        }
        if (request.getAvatarUrl() != null) {
            locationImage.setAvatarUrl(request.getAvatarUrl());
        } else if (request.getImageUrl() != null && locationImage.getAvatarUrl() == null) {
            locationImage.setAvatarUrl(request.getImageUrl());
        }
        if (request.getProvinceCode() != null) {
            locationImage.setProvinceCode(blankToNull(request.getProvinceCode()));
        }
        if (request.getPriority() != null) {
            locationImage.setPriority(request.getPriority());
        }
        if (request.getCoverageRadiusKm() != null) {
            locationImage.setCoverageRadiusKm(request.getCoverageRadiusKm());
        }
        if (request.getLatitude() != null) {
            locationImage.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            locationImage.setLongitude(request.getLongitude());
        }

        locationImage.setUpdatedAt(LocalDateTime.now());
        locationImage.normalizeAddress();
        locationImageRepository.update(locationImage);
        if (request.getWardCodes() != null) {
            locationImageRepository.replaceWardCodes(id, request.getWardCodes());
        }
        invalidateCaches();

        log.info("Location image updated: {}", id);
        LocationImageResponse response = mapToResponse(locationImage);
        response.setWardCodes(locationImageRepository.findWardCodes(id));
        return response;
    }

    @Override
    @Transactional
    public void deleteLocationImage(UUID id) {
        LocationImage locationImage = locationImageRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Location image not found"));

        imageStorageCleanupService.deleteImagesForEntityRecord("LOCATION_IMAGE", id);
        locationImageRepository.deleteById(id);
        invalidateCaches();
        log.info("Location image deleted: {}", id);
    }

    /** Curated location imagery goes through the shared upload door like everything else (MOD-05). */
    @Override
    public String uploadLocationImage(MultipartFile file) {
        ImageUploadOutcome outcome = fileUploadService.uploadImage(
                ImageUploadRequest.of(null, ImageUploadRequest.ImageEntryPoint.LOCATION_IMAGE, "location-images"),
                file);
        if (!outcome.isAccepted()) {
            throw new BusinessException(
                    outcome.isContentRejection()
                            ? ErrorConstant.IMAGE_REJECTED_BY_MODERATION
                            : ErrorConstant.INVALID_PARAMETERS,
                    outcome.failureMessage());
        }
        return outcome.url();
    }

    @Override
    public String uploadLocationVideo(MultipartFile file) {
        return fileUploadService.uploadVideo("city-stories", file);
    }

    private LocationImageResponse mapToResponse(LocationImage locationImage) {
        return mapToResponse(locationImage, null);
    }

    /**
     * List item: every field except {@code description}, which is null. The list query does not read it;
     * the app never shows it in a list and the admin editor loads the area by id.
     */
    private LocationImageResponse mapToListResponse(LocationImage locationImage, CityWeatherResponse weather) {
        LocationImageResponse response = mapToResponse(locationImage, weather);
        response.setDescription(null);
        return response;
    }

    private LocationImageResponse mapToResponse(LocationImage locationImage, CityWeatherResponse weather) {
        return LocationImageResponse.builder()
            .weather(weather)
            .id(locationImage.getId())
            .fullAddress(locationImage.getFullAddress())
            .citySlug(locationImage.getCitySlug())
            .provinceCode(locationImage.getProvinceCode())
            .slogan(locationImage.getSlogan())
            .description(deserializeDescription(locationImage.getDescription()))
            .imageUrl(locationImage.getImageUrl())
            .avatarUrl(locationImage.getAvatarUrl() != null
                    && !locationImage.getAvatarUrl().isBlank()
                    ? locationImage.getAvatarUrl()
                    : locationImage.getImageUrl())
            .latitude(locationImage.getLatitude())
            .longitude(locationImage.getLongitude())
            .priority(locationImage.getPriority())
            .coverageRadiusKm(locationImage.getCoverageRadiusKm())
            .createdAt(locationImage.getCreatedAt())
            .updatedAt(locationImage.getUpdatedAt())
            .build();
    }

    private List<LocationDescriptionSection> normalizeDescription(
            List<LocationDescriptionSection> sections) {
        Map<LocationDescriptionType, LocationDescriptionSection> byType = new EnumMap<>(LocationDescriptionType.class);
        if (sections != null) {
            sections.stream()
                .filter(section -> section != null && section.getType() != null)
                .forEach(section -> byType.put(section.getType(), section));
        }

        return java.util.Arrays.stream(LocationDescriptionType.values())
            .map(type -> {
                LocationDescriptionSection supplied = byType.get(type);
                String suppliedContent = supplied == null || supplied.getContent() == null
                    ? null
                    : supplied.getContent().getContent();
                return LocationDescriptionSection.builder()
                    .type(type)
                    .content(LocationDescriptionContent.builder()
                        .title(type.getDefaultTitle())
                        .content(suppliedContent)
                        .build())
                    .build();
            })
            .toList();
    }

    private String serializeDescription(List<LocationDescriptionSection> description) {
        try {
            return objectMapper.writeValueAsString(description);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                ErrorConstant.INTERNAL_SERVER_ERROR,
                "Failed to serialize location description"
            );
        }
    }

    private List<LocationDescriptionSection> deserializeDescription(String description) {
        if (description == null || description.isBlank()) {
            return normalizeDescription(null);
        }
        try {
            List<LocationDescriptionSection> parsed = objectMapper.readValue(
                description,
                new TypeReference<List<LocationDescriptionSection>>() { }
            );
            return normalizeDescription(parsed);
        } catch (JsonProcessingException exception) {
            log.warn("Invalid location description JSON; returning default sections", exception);
            return normalizeDescription(null);
        }
    }

    private String normalizeVietnamese(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String resolveAvatarUrl(String avatarUrl, String imageUrl) {
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            return avatarUrl;
        }
        return imageUrl;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

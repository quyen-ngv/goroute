package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.LocationDescriptionContent;
import com.ds.goroute.dto.LocationDescriptionSection;
import com.ds.goroute.dto.request.CreateLocationImageRequest;
import com.ds.goroute.dto.request.UpdateLocationImageRequest;
import com.ds.goroute.dto.response.LocationImageResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.enums.LocationDescriptionType;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.LocationImageService;
import com.ds.goroute.utils.CitySlugResolver;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationImageServiceImpl implements LocationImageService {

    private final LocationImageRepository locationImageRepository;
    private final StorageService storageService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_IMAGE = "https://images.unsplash.com/photo-1488646953014-85cb44e25828";

    @Override
    public String getImageForDestination(String destination) {
        if (destination == null || destination.isEmpty()) {
            return DEFAULT_IMAGE;
        }

        String normalized = normalizeVietnamese(destination.toLowerCase());

        return locationImageRepository.findBestMatch(normalized)
            .map(LocationImage::getImageUrl)
            .orElse(DEFAULT_IMAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationImageResponse> getAllLocationImages() {
        return locationImageRepository.findAll().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public LocationImageResponse getLocationImage(UUID id) {
        LocationImage locationImage = locationImageRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Location image not found"));
        return mapToResponse(locationImage);
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
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .priority(request.getPriority())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        locationImage.normalizeAddress();
        CitySlugResolver.resolveFromDestination(request.getFullAddress())
                .ifPresent(city -> locationImage.setCitySlug(city.getSlug()));
        locationImageRepository.insert(locationImage);

        log.info("Location image created: {}", locationImage.getId());
        return mapToResponse(locationImage);
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
        if (request.getPriority() != null) {
            locationImage.setPriority(request.getPriority());
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

        log.info("Location image updated: {}", id);
        return mapToResponse(locationImage);
    }

    @Override
    @Transactional
    public void deleteLocationImage(UUID id) {
        LocationImage locationImage = locationImageRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Location image not found"));

        imageStorageCleanupService.deleteImagesForEntityRecord("LOCATION_IMAGE", id);
        locationImageRepository.deleteById(id);
        log.info("Location image deleted: {}", id);
    }

    @Override
    public String uploadLocationImage(MultipartFile file) {
        try {
            String fileName = "location-images/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
            return storageService.uploadFile(
                fileName,
                file.getInputStream(),
                file.getContentType(),
                file.getSize()
            );
        } catch (Exception e) {
            log.error("Failed to upload location image", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Failed to upload image");
        }
    }

    private LocationImageResponse mapToResponse(LocationImage locationImage) {
        return LocationImageResponse.builder()
            .id(locationImage.getId())
            .fullAddress(locationImage.getFullAddress())
            .citySlug(locationImage.getCitySlug())
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
}

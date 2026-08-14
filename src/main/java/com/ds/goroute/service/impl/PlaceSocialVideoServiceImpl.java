package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.PlaceSocialVideoResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceSocialVideo;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.mapper.PlaceSocialVideoMapper;
import com.ds.goroute.service.PlaceSocialVideoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceSocialVideoServiceImpl implements PlaceSocialVideoService {
    private static final BigDecimal MATCH_DISTANCE_METERS = BigDecimal.valueOf(25);

    private final PlaceSocialVideoMapper videoMapper;
    private final PlaceMapper placeMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void syncSocialJob(SocialLocationJob job) {
        JsonNode root = parse(job.getResultPayload());
        if (root == null || !root.isObject()) {
            return;
        }

        JsonNode extraction = root.path("extraction");
        JsonNode candidates = extraction.path("candidates");
        if (!candidates.isArray()) {
            return;
        }

        JsonNode metadata = root.path("metadata");
        String canonicalUrl = text(metadata, "webpage_url", "webpageUrl");
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode candidate : candidates) {
            JsonNode mapCandidates = candidate.path("mapSearch").path("candidates");
            if (!mapCandidates.isArray()) {
                continue;
            }
            for (JsonNode mapCandidate : mapCandidates) {
                Place place = resolvePlace(mapCandidate);
                if (place == null) {
                    continue;
                }
                videoMapper.upsert(PlaceSocialVideo.builder()
                        .id(UUID.randomUUID())
                        .placeId(place.getId())
                        .socialJobId(job.getId())
                        .sourceUrl(job.getSourceUrl())
                        .canonicalUrl(canonicalUrl)
                        .platform(firstNonBlank(job.getPlatform(), text(metadata, "platform"), "social"))
                        .title(text(metadata, "title", "caption"))
                        .thumbnailUrl(text(metadata, "thumbnailUrl", "thumbnail_url", "thumbnail"))
                        .creatorName(text(metadata, "uploader", "creatorName", "authorName"))
                        .videoSummary(text(extraction, "summary"))
                        .videoUsefulSummary(text(extraction, "useful_summary", "usefulSummary"))
                        .generalGuidance(jsonArray(extraction.get("general_guidance")))
                        .placeRecap(text(candidate, "description", "place_recap", "placeRecap"))
                        .usefulInfo(jsonArray(first(candidate, "useful_info", "usefulInfo")))
                        .visitGuidance(jsonArray(first(candidate, "visit_guidance", "visitGuidance")))
                        .evidenceSources(jsonArray(first(candidate, "evidence_sources", "evidenceSources")))
                        .evidenceText(jsonArray(first(candidate, "evidence_text", "evidenceText")))
                        .confidence(decimal(candidate, "confidence"))
                        .language(firstNonBlank(job.getLanguage(), text(root, "language"), null))
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlaceSocialVideoResponse> findByPlaceId(UUID placeId) {
        return videoMapper.findByPlaceId(placeId).stream()
                .map(this::toResponse)
                .toList();
    }

    private PlaceSocialVideoResponse toResponse(PlaceSocialVideo video) {
        return PlaceSocialVideoResponse.builder()
                .id(video.getId())
                .sourceUrl(firstNonBlank(video.getCanonicalUrl(), video.getSourceUrl(), null))
                .platform(video.getPlatform())
                .title(video.getTitle())
                .thumbnailUrl(video.getThumbnailUrl())
                .creatorName(video.getCreatorName())
                .videoSummary(video.getVideoSummary())
                .videoUsefulSummary(video.getVideoUsefulSummary())
                .generalGuidance(readStringList(video.getGeneralGuidance()))
                .placeRecap(video.getPlaceRecap())
                .usefulInfo(readStringList(video.getUsefulInfo()))
                .visitGuidance(readStringList(video.getVisitGuidance()))
                .evidenceSources(readStringList(video.getEvidenceSources()))
                .evidenceText(readStringList(video.getEvidenceText()))
                .confidence(video.getConfidence())
                .language(video.getLanguage())
                .createdAt(video.getCreatedAt())
                .build();
    }

    private Place resolvePlace(JsonNode mapCandidate) {
        String mappedId = text(mapCandidate.path("placeMapping"), "placeId");
        if (mappedId != null) {
            try {
                Place mapped = placeMapper.findById(UUID.fromString(mappedId));
                if (mapped != null) {
                    return mapped;
                }
            } catch (IllegalArgumentException ignored) {
                log.debug("Ignoring invalid mapped place UUID: {}", mappedId);
            }
        }

        String googlePlaceId = text(mapCandidate, "placeId", "googlePlaceId");
        if (googlePlaceId != null) {
            Place place = placeMapper.findByPlaceId(googlePlaceId);
            if (place != null) {
                return place;
            }
        }
        String cid = text(mapCandidate, "cid");
        if (cid != null) {
            Place place = placeMapper.findByCid(cid);
            if (place != null) {
                return place;
            }
        }
        BigDecimal latitude = decimal(mapCandidate, "latitude");
        BigDecimal longitude = decimal(mapCandidate, "longitude");
        return latitude == null || longitude == null
                ? null
                : placeMapper.findNearCoordinates(latitude, longitude, MATCH_DISTANCE_METERS);
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            log.warn("Could not parse social job result while linking videos: {}", e.getMessage());
            return null;
        }
    }

    private String jsonArray(JsonNode value) {
        return value != null && value.isArray() ? value.toString() : "[]";
    }

    private List<String> readStringList(String value) {
        JsonNode node = parse(value);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                items.add(item.asText().trim());
            }
        }
        return items;
    }

    private JsonNode first(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }
}

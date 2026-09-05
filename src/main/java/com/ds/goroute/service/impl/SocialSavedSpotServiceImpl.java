package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.SocialSavedSpotResponse;
import com.ds.goroute.entity.SocialSavedSpot;
import com.ds.goroute.mapper.SocialSavedSpotMapper;
import com.ds.goroute.service.SocialSavedSpotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialSavedSpotServiceImpl implements SocialSavedSpotService {
    private static final int UPSERT_BATCH_SIZE = 100;

    private final SocialSavedSpotMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public int saveFromJob(UUID userId, UUID socialJobId, JsonNode result) {
        JsonNode extraction = result == null ? null : result.path("extraction");
        JsonNode candidates = extraction == null ? null : extraction.path("candidates");
        if (candidates == null || !candidates.isArray()) return 0;
        String contentType = text(extraction, "contentType");
        if (contentType == null) contentType = "PLACE_LIST";
        List<SocialSavedSpot> batch = new ArrayList<>(UPSERT_BATCH_SIZE);
        int saved = 0;
        int position = 0;
        for (JsonNode candidate : candidates) {
            if (candidate == null || !candidate.isObject()) continue;
            position++;
            String name = firstText(candidate, "name", "query");
            if (name == null) continue;
            SocialSavedSpot spot = mapCandidate(userId, socialJobId, contentType, candidate, position);
            batch.add(spot);
            if (batch.size() >= UPSERT_BATCH_SIZE) {
                mapper.upsertAll(List.copyOf(batch));
                saved += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            mapper.upsertAll(List.copyOf(batch));
            saved += batch.size();
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SocialSavedSpotResponse> listMine(UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return mapper.findByUserId(userId, safeSize, safePage * safeSize).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID spotId) {
        if (mapper.deleteOwned(userId, spotId) == 0) {
            throw new IllegalArgumentException("Social saved spot not found");
        }
    }

    private SocialSavedSpot mapCandidate(UUID userId, UUID socialJobId, String contentType,
                                         JsonNode candidate, int position) {
        JsonNode mapCandidate = candidate.path("mapSearch").path("candidates").isArray()
                && candidate.path("mapSearch").path("candidates").size() > 0
                ? candidate.path("mapSearch").path("candidates").get(0) : null;
        JsonNode mapping = mapCandidate == null ? null : mapCandidate.path("placeMapping");
        String candidateRef = firstText(candidate, "candidateRef");
        if (candidateRef == null) candidateRef = String.format("social-%04d", position);
        UUID placeId = uuid(text(mapping, "placeId"));
        String resolutionStatus = firstText(candidate, "resolutionStatus");
        if (resolutionStatus == null) {
            resolutionStatus = mapCandidate == null ? "UNRESOLVED" : "RESOLVED";
        }
        String identityStatus = firstText(candidate, "identity_status", "identityStatus");
        if (identityStatus == null) identityStatus = text(candidate.path("verification"), "status");
        String addressHint = firstText(candidate, "address_hint", "addressHint");
        if (addressHint == null && mapCandidate != null) {
            addressHint = firstText(mapCandidate, "address", "formattedAddress", "formatted_address");
        }
        if (addressHint == null && mapping != null) {
            addressHint = firstText(mapping, "address", "formattedAddress", "formatted_address");
        }
        return SocialSavedSpot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .socialJobId(socialJobId)
                .candidateRef(limit(candidateRef, 128))
                .contentType(limit(contentType, 20))
                .name(limit(firstText(candidate, "name", "query"), 255))
                .query(limit(firstText(candidate, "query"), 500))
                .description(text(candidate, "description"))
                .usefulInfo(json(candidate.get("useful_info"), candidate.get("usefulInfo")))
                .visitGuidance(json(candidate.get("visit_guidance"), candidate.get("visitGuidance")))
                .addressHint(limit(addressHint, 500))
                .latitude(decimal(firstText(candidate, "latitude"), mapCandidate == null ? null : text(mapCandidate, "latitude")))
                .longitude(decimal(firstText(candidate, "longitude"), mapCandidate == null ? null : text(mapCandidate, "longitude")))
                .googlePlaceId(mapCandidate == null ? null : limit(firstText(mapCandidate, "placeId", "googlePlaceId"), 255))
                .placeId(placeId)
                .dayHint(integer(candidate, "dayHint", "day_hint"))
                .sequence(integer(candidate, "sequence") == null ? position : integer(candidate, "sequence"))
                .timeHint(limit(firstText(candidate, "timeHint", "time_hint"), 64))
                .optionGroupId(limit(firstText(candidate, "optionGroupId", "option_group_id"), 128))
                .optionIndex(integer(candidate, "optionIndex", "option_index"))
                .relation(limit(firstText(candidate, "relation"), 32))
                .identityStatus(limit(identityStatus, 32))
                .resolutionStatus(limit(resolutionStatus, 32))
                .evidence(json(candidate.get("evidence")))
                .imageUrl(mapCandidate == null ? null : limit(firstText(mapCandidate, "thumbnail", "thumbnailUrl", "imageUrl", "photoUrl"), 1000))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SocialSavedSpotResponse toResponse(SocialSavedSpot spot) {
        return SocialSavedSpotResponse.builder()
                .id(spot.getId()).socialJobId(spot.getSocialJobId()).candidateRef(spot.getCandidateRef())
                .contentType(spot.getContentType()).name(spot.getName()).query(spot.getQuery())
                .description(spot.getDescription()).usefulInfo(tree(spot.getUsefulInfo()))
                .visitGuidance(tree(spot.getVisitGuidance())).addressHint(spot.getAddressHint())
                .latitude(spot.getLatitude()).longitude(spot.getLongitude()).googlePlaceId(spot.getGooglePlaceId())
                .placeId(spot.getPlaceId()).dayHint(spot.getDayHint()).sequence(spot.getSequence())
                .timeHint(spot.getTimeHint()).optionGroupId(spot.getOptionGroupId()).optionIndex(spot.getOptionIndex())
                .relation(spot.getRelation()).identityStatus(spot.getIdentityStatus())
                .resolutionStatus(spot.getResolutionStatus()).evidence(tree(spot.getEvidence()))
                .imageUrl(spot.getImageUrl()).createdAt(spot.getCreatedAt()).build();
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isNull()) return null;
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return null;
        String value = node.get(field).asText().trim();
        return value.isEmpty() ? null : value;
    }

    private Integer integer(JsonNode node, String... fields) {
        String raw = firstText(node, fields);
        if (raw == null) return null;
        try { return Integer.valueOf(raw); } catch (NumberFormatException ignored) { return null; }
    }

    private BigDecimal decimal(String primary, String fallback) {
        String raw = primary != null ? primary : fallback;
        try { return raw == null ? null : new BigDecimal(raw); } catch (NumberFormatException ignored) { return null; }
    }

    private UUID uuid(String value) {
        try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private String json(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isNull()) {
                try { return objectMapper.writeValueAsString(node); } catch (Exception ignored) { return null; }
            }
        }
        return null;
    }

    private JsonNode tree(String value) {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readTree(value); } catch (Exception ignored) { return null; }
    }

    private String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

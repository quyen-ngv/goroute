package com.ds.goroute.service;

import com.ds.goroute.dto.request.AiTripDestinationRequest;
import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts one completed social extraction into the internal AI-trip request contract.
 *
 * <p>The social extractor owns interpretation of the video. This adapter deliberately keeps
 * every candidate in source order, including unresolved mentions and alternatives, and only
 * adds the fields required by the itinerary worker.</p>
 */
@Component
@RequiredArgsConstructor
public class SocialItineraryRequestFactory {
    private static final String ITINERARY = "ITINERARY";
    private static final String FOOD = "FOOD_AND_DRINK";

    private final BusinessConfigService businessConfigService;

    public AiTripGenerateRequest build(SocialLocationJob job, JsonNode result) {
        JsonNode extraction = result == null ? null : result.path("extraction");
        if (extraction == null || !ITINERARY.equalsIgnoreCase(text(extraction, "contentType"))) {
            return null;
        }

        JsonNode candidates = extraction.path("candidates");
        List<JsonNode> sourceCandidates = new ArrayList<>();
        if (candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                if (candidate != null && candidate.isObject()) sourceCandidates.add(candidate);
            }
        }

        Integer durationDays = durationDays(extraction, sourceCandidates);
        if (durationDays == null) {
            // The extractor is the AI decision-maker for duration. Do not silently turn a
            // malformed itinerary payload into a one-day/default itinerary; the saved spots
            // projection can still complete and the cached result can be retried later.
            return null;
        }
        LocalDate startDate = LocalDate.now()
                .plusDays(Math.max(0, businessConfigService.getInt(BusinessConfigKey.SOCIAL_START_OFFSET_DAYS)));
        LocalDate endDate = startDate.plusDays(durationDays - 1L);

        JsonNode destination = extraction.path("destination");
        String destinationName = first(destination, "name", "city", "region", "country");
        if (destinationName == null) destinationName = firstCandidateContext(sourceCandidates);
        if (destinationName == null) destinationName = "Social video itinerary";
        String cityName = first(destination, "city", "name", "region", "country");
        if (cityName == null) cityName = destinationName;

        Coordinates center = coordinates(destination, sourceCandidates);
        List<Map<String, Object>> rows = new ArrayList<>(sourceCandidates.size());
        for (int index = 0; index < sourceCandidates.size(); index++) {
            rows.add(candidateRow(job, sourceCandidates.get(index), index + 1));
        }

        Map<String, Object> socialContext = new LinkedHashMap<>();
        socialContext.put("source", "social_video");
        socialContext.put("socialJobId", job.getId().toString());
        socialContext.put("sourceUrl", job.getSourceUrl());
        socialContext.put("contentType", ITINERARY);
        socialContext.put("durationDays", durationDays);
        socialContext.put("durationBasis", text(extraction, "durationBasis"));
        socialContext.put("durationConfidence", decimal(extraction, "durationConfidence"));
        socialContext.put("destination", destination == null || destination.isMissingNode()
                ? Map.of("name", destinationName) : destination);
        socialContext.put("candidates", rows);

        String summary = first(extraction, "summary");
        String tripName = summary == null ? destinationName + " itinerary" : summary;
        return AiTripGenerateRequest.builder()
                .tripName(limit(tripName, 255))
                .cityName(limit(cityName, 255))
                .cityLat(center.latitude())
                .cityLng(center.longitude())
                .destinations(List.of(AiTripDestinationRequest.builder()
                        .name(limit(destinationName, 255))
                        // Trip creation resolves the cover through LocationImageService, which
                        // now uses the shared configurable fallback when there is no match.
                        .locationImageId(null)
                        .startDate(startDate)
                        .endDate(endDate)
                        .latitude(center.latitude())
                        .longitude(center.longitude())
                        .orderIndex(0)
                        .build()))
                .startDate(startDate)
                .endDate(endDate)
                .dayCount(durationDays)
                .pace("BALANCED")
                .socialContext(socialContext)
                .build();
    }

    private Map<String, Object> candidateRow(SocialLocationJob job, JsonNode candidate, int sourcePosition) {
        JsonNode mapCandidate = firstMapCandidate(candidate);
        JsonNode mapping = mapCandidate == null ? null : mapCandidate.path("placeMapping");
        String title = first(candidate, "name", "query");
        if (title == null && mapCandidate != null) title = first(mapCandidate, "title", "name");
        if (title == null) title = "Unnamed video activity";

        Map<String, Object> row = new LinkedHashMap<>();
        String placeId = mapping == null ? null : first(mapping, "placeId");
        String googlePlaceId = mapCandidate == null ? null : first(mapCandidate, "placeId", "googlePlaceId");
        put(row, "id", placeId);
        put(row, "candidateId", first(candidate, "candidateRef", "candidateId"));
        put(row, "googlePlaceId", googlePlaceId);
        row.put("title", limit(title, 255));
        String address = first(candidate, "address_hint", "addressHint", "city_hint", "region_hint");
        if (address == null && mapCandidate != null) {
            address = first(mapCandidate, "address", "formattedAddress", "formatted_address");
        }
        if (address == null && mapping != null) {
            address = first(mapping, "address", "formattedAddress", "formatted_address");
        }
        put(row, "address", address);
        BigDecimal latitude = decimal(candidate, "latitude");
        BigDecimal longitude = decimal(candidate, "longitude");
        if (latitude == null && mapCandidate != null) latitude = decimal(mapCandidate, "latitude");
        if (longitude == null && mapCandidate != null) longitude = decimal(mapCandidate, "longitude");
        put(row, "latitude", latitude);
        put(row, "longitude", longitude);
        row.put("placeGroup", inferPlaceGroup(candidate));
        put(row, "category", first(candidate, "place_type_hint", "mention_type"));
        put(row, "description", first(candidate, "description"));
        put(row, "visitDurationMinutes", integer(candidate, "visitDurationMinutes"));
        row.put("source", "social");
        row.put("socialJobId", job.getId().toString());
        Integer sequence = integer(candidate, "sequence");
        row.put("socialSequence", sequence == null ? sourcePosition : sequence);
        put(row, "dayHint", integer(candidate, "dayHint", "day_hint"));
        put(row, "timeHint", first(candidate, "timeHint", "time_hint"));
        put(row, "optionGroupId", first(candidate, "optionGroupId", "option_group_id"));
        put(row, "optionIndex", integer(candidate, "optionIndex", "option_index"));
        put(row, "relation", first(candidate, "relation"));
        row.put("resolutionStatus", first(candidate, "resolutionStatus") == null
                ? (mapCandidate == null ? "UNRESOLVED" : "RESOLVED")
                : first(candidate, "resolutionStatus"));
        row.put("isPinned", true);
        return row;
    }

    private Integer durationDays(JsonNode extraction, List<JsonNode> candidates) {
        Integer explicit = integer(extraction, "durationDays");
        if (explicit != null && explicit > 0) return explicit;

        int maxDayHint = candidates.stream()
                .map(candidate -> integer(candidate, "dayHint", "day_hint"))
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        if (maxDayHint > 0) return maxDayHint;
        // A normal ITINERARY extraction always contains durationDays chosen by the extractor,
        // including when the video never states a duration. Returning null here prevents a
        // fixed or code-derived duration from overriding that AI decision.
        return null;
    }

    private Coordinates coordinates(JsonNode destination, List<JsonNode> candidates) {
        BigDecimal latitude = decimal(destination, "latitude", "lat");
        BigDecimal longitude = decimal(destination, "longitude", "lng", "lon");
        if (latitude != null && longitude != null) return new Coordinates(latitude, longitude);
        for (JsonNode candidate : candidates) {
            JsonNode map = firstMapCandidate(candidate);
            BigDecimal candidateLat = decimal(candidate, "latitude");
            BigDecimal candidateLng = decimal(candidate, "longitude");
            if (candidateLat == null && map != null) candidateLat = decimal(map, "latitude");
            if (candidateLng == null && map != null) candidateLng = decimal(map, "longitude");
            if (candidateLat != null && candidateLng != null) return new Coordinates(candidateLat, candidateLng);
        }
        return new Coordinates(null, null);
    }

    private String inferPlaceGroup(JsonNode candidate) {
        String value = (first(candidate, "place_type_hint", "mention_type", "category") + " "
                + first(candidate, "name", "query")).toLowerCase(Locale.ROOT);
        if (value.matches(".*(restaurant|cafe|coffee|food|drink|bar|bakery|dessert|\u0103n|u\u1ed1ng|qu\u00e1n).*")) {
            return FOOD;
        }
        if (value.matches(".*(museum|temple|pagoda|church|heritage|culture|\u0111\u1ec1n|ch\u00f9a).*")) {
            return "CULTURE_AND_HERITAGE";
        }
        if (value.matches(".*(beach|park|mountain|nature|waterfall|island|bi\u1ec3n|n\u00fai|th\u00e1c).*")) {
            return "NATURE_AND_OUTDOORS";
        }
        if (value.matches(".*(shop|market|mall|shopping|ch\u1ee3|mua s\u1eafm).*")) {
            return "SHOPPING_AND_MARKET";
        }
        if (value.matches(".*(attraction|theme park|show|cinema|karaoke|tour|activity).*")) {
            return "ATTRACTIONS";
        }
        return "OTHER";
    }

    private String firstCandidateContext(List<JsonNode> candidates) {
        for (JsonNode candidate : candidates) {
            String value = first(candidate, "city_hint", "region_hint", "country_hint");
            if (value != null) return value;
        }
        return null;
    }

    private JsonNode firstMapCandidate(JsonNode candidate) {
        JsonNode maps = candidate == null ? null : candidate.path("mapSearch").path("candidates");
        return maps != null && maps.isArray() && !maps.isEmpty() ? maps.get(0) : null;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String string) || !string.isBlank())) target.put(key, value);
    }

    private String first(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) return value.asText().trim();
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        return first(node, field);
    }

    private Integer integer(JsonNode node, String... fields) {
        if (node == null || node.isNull()) return null;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) return value.asInt();
            if (value != null && value.isTextual()) {
                try { return Integer.valueOf(value.asText().trim()); } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String... fields) {
        if (node == null || node.isNull()) return null;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull() || value.asText().isBlank()) continue;
            try { return new BigDecimal(value.asText()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private record Coordinates(BigDecimal latitude, BigDecimal longitude) { }
}

package com.ds.goroute.service.place;

import com.ds.goroute.dto.response.PlaceEditChangeResponse;
import com.ds.goroute.dto.response.PlaceEditSuggestionResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceEditChange;
import com.ds.goroute.entity.PlaceEditSuggestion;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.service.PlaceTranslationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Entities of place edit suggestions to their wire shapes (plan §3.11). */
@Component
@RequiredArgsConstructor
public class PlaceEditResponseAssembler {

    private final ObjectMapper objectMapper;

    public PlaceEditChangeResponse change(PlaceEditChange change) {
        return PlaceEditChangeResponse.builder()
                .id(change.getId())
                .field(change.getField() == null ? null : change.getField().name())
                .author(change.getAuthor() == null ? null : change.getAuthor().name())
                .status(change.getStatus() == null ? null : change.getStatus().name())
                .rejectReason(change.getRejectReason() == null ? null : change.getRejectReason().name())
                .oldValue(readTree(change.getOldValue()))
                .newValue(readTree(change.getNewValue()))
                .appliedValue(readTree(change.getAppliedValue()))
                .build();
    }

    /**
     * @param place        list columns are enough (title, thumbnail); null when the place is gone
     * @param includeNote  the operator's internal note goes to the console only
     */
    public PlaceEditSuggestionResponse suggestion(PlaceEditSuggestion suggestion, List<PlaceEditChange> changes,
                                                  Place place, List<PlaceTranslation> translations,
                                                  String locale, boolean includeNote) {
        return PlaceEditSuggestionResponse.builder()
                .id(suggestion.getId())
                .placeId(suggestion.getPlaceId())
                .placeName(placeName(place, translations, locale))
                .placeThumbnail(place == null ? null : place.getThumbnail())
                .type(suggestion.getType() == null ? null : suggestion.getType().name())
                .status(suggestion.getStatus() == null ? null : suggestion.getStatus().name())
                .description(suggestion.getDescription())
                .evidencePhotos(photos(suggestion.getEvidencePhotos()))
                .locale(suggestion.getLocale())
                .rejectReason(suggestion.getRejectReason() == null ? null : suggestion.getRejectReason().name())
                .reviewNote(includeNote ? suggestion.getReviewNote() : null)
                .createdAt(suggestion.getCreatedAt())
                .reviewedAt(suggestion.getReviewedAt())
                .changes(changes == null ? List.of() : changes.stream().map(this::change).toList())
                .build();
    }

    public static String placeName(Place place, List<PlaceTranslation> translations, String locale) {
        String localized = PlaceTranslationService.localizedName(translations, locale);
        if (localized != null) {
            return localized;
        }
        return place == null ? null : place.getTitle();
    }

    public List<String> photos(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> photos = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return photos == null ? List.of() : photos;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return List.of();
        }
    }

    public JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node == null || node.isNull() ? null : node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return null;
        }
    }

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write JSON", exception);
        }
    }
}

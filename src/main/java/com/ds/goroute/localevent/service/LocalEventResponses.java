package com.ds.goroute.localevent.service;

import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.domain.LocalEventImage;
import com.ds.goroute.localevent.dto.AdminLocalEventDetailResponse;
import com.ds.goroute.localevent.dto.AdminLocalEventImageResponse;
import com.ds.goroute.localevent.dto.AdminLocalEventRow;
import com.ds.goroute.localevent.dto.LocalEventDetailFields;
import com.ds.goroute.localevent.dto.LocalEventDetailResponse;
import com.ds.goroute.localevent.dto.LocalEventDuplicateRef;
import com.ds.goroute.localevent.dto.LocalEventFieldError;
import com.ds.goroute.localevent.dto.LocalEventImageResponse;
import com.ds.goroute.localevent.dto.LocalEventSessionResponse;
import com.ds.goroute.localevent.dto.LocalEventSummaryResponse;
import com.ds.goroute.type.LocalEventAttendanceMode;
import com.ds.goroute.type.LocalEventCategory;
import com.ds.goroute.type.LocalEventImageStatus;
import com.ds.goroute.type.LocalEventPublishStatus;
import com.ds.goroute.type.LocalEventStatus;
import com.ds.goroute.type.LocalEventTicketAvailability;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Row → response conversion for every local event endpoint; parses the JSON columns. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalEventResponses {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<LocalEventFieldError>> ERROR_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public LocalEventSummaryResponse summary(LocalEvent event) {
        LocalEventSummaryResponse response = new LocalEventSummaryResponse();
        fillSummary(response, event);
        return response;
    }

    public LocalEventDetailResponse publicDetail(LocalEvent event, List<LocalEventImage> images) {
        LocalEventDetailResponse response = new LocalEventDetailResponse();
        fillDetail(response, event);
        response.setImages(images.stream()
                .filter(image -> LocalEventImageStatus.DONE.name().equals(image.getStatus()) && image.getUrl() != null)
                .map(image -> new LocalEventImageResponse(image.getUrl(), image.getCredit()))
                .toList());
        return response;
    }

    public AdminLocalEventDetailResponse adminDetail(LocalEvent event, List<LocalEventImage> images,
                                                     LocalEvent duplicateOf) {
        AdminLocalEventDetailResponse response = new AdminLocalEventDetailResponse();
        fillDetail(response, event);
        response.setPublishStatus(enumOf(LocalEventPublishStatus.class, event.getPublishStatus()));
        response.setPublishedAt(event.getPublishedAt());
        response.setExternalId(event.getExternalId());
        response.setSourceUpdatedAt(event.getSourceUpdatedAt());
        response.setContentHash(event.getContentHash());
        response.setExtractionConfidence(event.getExtractionConfidence());
        response.setExtractedBy(event.getExtractedBy());
        response.setRawPayload(jsonNode(event.getRawPayload()));
        response.setDuplicateOfEventId(event.getDuplicateOfEventId());
        if (duplicateOf != null) {
            response.setDuplicateOf(LocalEventDuplicateRef.builder()
                    .id(duplicateOf.getId())
                    .title(duplicateOf.getTitle())
                    .startAt(duplicateOf.getStartAt())
                    .venueName(duplicateOf.getVenueName())
                    .build());
        }
        response.setManuallyEdited(Boolean.TRUE.equals(event.getManuallyEdited()));
        response.setDeleted(Boolean.TRUE.equals(event.getIsDeleted()));
        response.setImages(images.stream().map(this::adminImage).toList());
        response.setCreatedBy(event.getCreatedBy());
        response.setUpdatedBy(event.getUpdatedBy());
        response.setCreatedAt(event.getCreatedAt());
        response.setDataVersion(event.getDataVersion());
        return response;
    }

    public AdminLocalEventRow adminRow(LocalEvent event, int pendingImages) {
        return AdminLocalEventRow.builder()
                .id(event.getId())
                .title(event.getTitle())
                .category(enumOf(LocalEventCategory.class, event.getCategory()))
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .venueName(event.getVenueName())
                .provinceCode(event.getProvinceCode())
                .coverUrl(event.getCoverUrl())
                .publishStatus(enumOf(LocalEventPublishStatus.class, event.getPublishStatus()))
                .eventStatus(enumOf(LocalEventStatus.class, event.getEventStatus()))
                .source(event.getSource())
                .externalId(event.getExternalId())
                .duplicateOfEventId(event.getDuplicateOfEventId())
                .manuallyEdited(Boolean.TRUE.equals(event.getManuallyEdited()))
                .pendingImages(pendingImages)
                .updatedAt(event.getUpdatedAt())
                .build();
    }

    public List<LocalEventFieldError> errors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, ERROR_LIST);
        } catch (Exception exception) {
            log.warn("Unreadable import item errors JSON");
            return List.of();
        }
    }

    private AdminLocalEventImageResponse adminImage(LocalEventImage image) {
        return AdminLocalEventImageResponse.builder()
                .id(image.getId())
                .position(image.getPosition())
                .url(image.getUrl())
                .sourceUrl(image.getSourceUrl())
                .credit(image.getCredit())
                .status(enumOf(LocalEventImageStatus.class, image.getStatus()))
                .attempts(image.getAttempts())
                .failureReason(image.getFailureReason())
                .build();
    }

    private void fillSummary(LocalEventSummaryResponse response, LocalEvent event) {
        response.setId(event.getId());
        response.setTitle(event.getTitle());
        response.setCategory(enumOf(LocalEventCategory.class, event.getCategory()));
        response.setStartAt(event.getStartAt());
        response.setEndAt(event.getEndAt());
        response.setTimezone(event.getTimezone());
        response.setAllDay(Boolean.TRUE.equals(event.getAllDay()));
        response.setEventStatus(enumOf(LocalEventStatus.class, event.getEventStatus()));
        response.setVenueName(event.getVenueName());
        response.setAddress(event.getAddress());
        response.setLatitude(event.getLatitude());
        response.setLongitude(event.getLongitude());
        response.setDistanceKm(event.getDistanceKm() == null ? null : Math.round(event.getDistanceKm() * 100) / 100.0);
        response.setCoverUrl(event.getCoverUrl());
        response.setIsFree(event.getIsFree());
        response.setPriceMin(event.getPriceMin());
        response.setPriceMax(event.getPriceMax());
        response.setCurrency(event.getCurrency());
        response.setTicketAvailability(enumOf(LocalEventTicketAvailability.class, event.getTicketAvailability()));
        response.setSessionCount(event.getSessionCount() != null ? event.getSessionCount() : sessions(event).size());
    }

    private void fillDetail(LocalEventDetailFields response, LocalEvent event) {
        fillSummary(response, event);
        List<LocalEventSessionResponse> sessions = sessions(event);
        response.setSessionCount(sessions.size());
        response.setSummary(event.getSummary());
        response.setDescription(event.getDescription());
        response.setLanguage(event.getLanguage());
        response.setTags(stringList(event.getTags()));
        response.setSessions(sessions);
        response.setScheduleNote(event.getScheduleNote());
        response.setLunarDateText(event.getLunarDateText());
        response.setPreviousStartAt(event.getPreviousStartAt());
        response.setAttendanceMode(enumOf(LocalEventAttendanceMode.class, event.getAttendanceMode()));
        response.setOnlineUrl(event.getOnlineUrl());
        response.setPlaceId(event.getPlaceId());
        response.setProvinceCode(event.getProvinceCode());
        response.setWardCode(event.getWardCode());
        response.setPriceNote(event.getPriceNote());
        response.setTicketUrl(event.getTicketUrl());
        response.setOrganizerName(event.getOrganizerName());
        response.setOrganizerUrl(event.getOrganizerUrl());
        response.setPerformers(stringList(event.getPerformers()));
        response.setMinAge(event.getMinAge());
        response.setEndAtEstimated(Boolean.TRUE.equals(event.getEndAtEstimated()));
        response.setSource(event.getSource());
        response.setSourceUrl(event.getSourceUrl());
        response.setUpdatedAt(event.getUpdatedAt());
    }

    private List<LocalEventSessionResponse> sessions(LocalEvent event) {
        List<LocalEventSessionResponse> sessions = new ArrayList<>();
        JsonNode array = jsonNode(event.getSessions());
        if (array == null || !array.isArray()) {
            return sessions;
        }
        for (JsonNode node : array) {
            LocalDateTime start = time(node.get("startAt"));
            if (start == null) {
                continue;
            }
            sessions.add(LocalEventSessionResponse.builder()
                    .startAt(start)
                    .endAt(time(node.get("endAt")))
                    .label(node.hasNonNull("label") ? node.get("label").asText() : null)
                    .status(node.hasNonNull("status")
                            ? enumOf(LocalEventStatus.class, node.get("status").asText())
                            : LocalEventStatus.SCHEDULED)
                    .build());
        }
        return sessions;
    }

    private LocalDateTime time(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return LocalDateTime.parse(node.asText());
        } catch (Exception exception) {
            return null;
        }
    }

    private List<String> stringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private JsonNode jsonNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

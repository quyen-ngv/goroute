package com.ds.goroute.localevent.service;

import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.domain.LocalEventDuplicateCandidate;
import com.ds.goroute.localevent.domain.LocalEventImage;
import com.ds.goroute.localevent.persistence.LocalEventDuplicateSearchParams;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.LocalEventImageStatus;
import com.ds.goroute.type.LocalEventPublishStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a {@link NormalizedLocalEvent} into rows: the event row, its image rows (re-using the
 * re-hosting state of images that did not change) and the fuzzy duplicate check. Shared by the
 * console create/edit and the import so both write exactly the same shape.
 */
@Component
@RequiredArgsConstructor
public class LocalEventAssembler {

    /** pg_trgm similarity of normalised titles from which two events count as the same. */
    static final double DUPLICATE_MIN_SIMILARITY = 0.6;
    /** Two events closer than this (km) with similar titles and dates are likely the same. */
    static final double DUPLICATE_RADIUS_KM = 2.0;
    private static final double KM_PER_DEGREE = 111.32;
    /** Session times in the JSON column: UTC, seconds always written, no offset. */
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final LocalEventRepository repository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    /** A new event row; publish status (and published_at) come from the request on create only. */
    public LocalEvent newEvent(NormalizedLocalEvent normalized, UUID id, String source, UUID actorUserId,
                               LocalDateTime now) {
        LocalEvent event = LocalEvent.builder()
                .id(id)
                .source(source)
                .externalId(normalized.getExternalId())
                .publishStatus(normalized.getPublishStatus())
                .publishedAt(LocalEventPublishStatus.PUBLISHED.name().equals(normalized.getPublishStatus()) ? now : null)
                .manuallyEdited(false)
                .isDeleted(false)
                .dataVersion(0L)
                .createdBy(actorUserId)
                .createdAt(now)
                .build();
        applyContent(event, normalized, actorUserId, now);
        return event;
    }

    /** Copies every content field onto {@code event}; publish status and cover are left as they are. */
    public void applyContent(LocalEvent event, NormalizedLocalEvent normalized, UUID actorUserId, LocalDateTime now) {
        event.setTitle(normalized.getTitle());
        event.setTitleNorm(normalized.getTitleNorm());
        event.setSummary(normalized.getSummary());
        event.setDescription(normalized.getDescription());
        event.setLanguage(normalized.getLanguage());
        event.setCategory(normalized.getCategory());
        event.setTags(toJson(normalized.getTags()));
        event.setStartAt(normalized.getStartAt());
        event.setEndAt(normalized.getEndAt());
        event.setEndAtEstimated(normalized.isEndAtEstimated());
        event.setTimezone(normalized.getTimezone());
        event.setAllDay(normalized.isAllDay());
        event.setSessions(sessionsJson(normalized.getSessions()));
        event.setScheduleNote(normalized.getScheduleNote());
        event.setLunarDateText(normalized.getLunarDateText());
        event.setEventStatus(normalized.getEventStatus());
        event.setPreviousStartAt(normalized.getPreviousStartAt());
        event.setAttendanceMode(normalized.getAttendanceMode());
        event.setOnlineUrl(normalized.getOnlineUrl());
        event.setVenueName(normalized.getVenueName());
        event.setAddress(normalized.getAddress());
        event.setLatitude(normalized.getLatitude());
        event.setLongitude(normalized.getLongitude());
        event.setPlaceId(normalized.getPlaceId());
        event.setProvinceCode(normalized.getProvinceCode());
        event.setWardCode(normalized.getWardCode());
        event.setIsFree(normalized.getIsFree());
        event.setPriceMin(normalized.getPriceMin());
        event.setPriceMax(normalized.getPriceMax());
        event.setCurrency(normalized.getCurrency());
        event.setPriceNote(normalized.getPriceNote());
        event.setTicketUrl(normalized.getTicketUrl());
        event.setTicketAvailability(normalized.getTicketAvailability());
        event.setOrganizerName(normalized.getOrganizerName());
        event.setOrganizerUrl(normalized.getOrganizerUrl());
        event.setPerformers(toJson(normalized.getPerformers()));
        event.setMinAge(normalized.getMinAge());
        event.setSourceUrl(normalized.getSourceUrl());
        event.setContentHash(normalized.getContentHash());
        event.setUpdatedBy(actorUserId);
        event.setUpdatedAt(now);
    }

    /** Copies the crawler's metadata (only present on imports). */
    public void applyImportMetadata(LocalEvent event, NormalizedLocalEvent normalized) {
        event.setSourceUpdatedAt(normalized.getSourceUpdatedAt());
        event.setExtractionConfidence(normalized.getExtractionConfidence());
        event.setExtractedBy(normalized.getExtractedBy());
        event.setRawPayload(normalized.getRawPayload());
    }

    /** True when the requested image list differs from the stored one (order, URL or credit). */
    public boolean imagesChanged(List<NormalizedLocalEvent.Image> requested, List<LocalEventImage> existing) {
        if (requested.size() != existing.size()) {
            return true;
        }
        for (int index = 0; index < requested.size(); index++) {
            NormalizedLocalEvent.Image wanted = requested.get(index);
            LocalEventImage stored = existing.get(index);
            if (!matches(stored, wanted.url()) || !Objects.equals(stored.getCredit(), wanted.credit())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The image rows for {@code requested}, in order. An image already stored (matched by its
     * source URL or its re-hosted URL) keeps its state, so re-saving does not download it again;
     * a URL on our own storage is DONE at once; anything else waits for the job as PENDING.
     */
    public List<LocalEventImage> planImages(UUID eventId, List<NormalizedLocalEvent.Image> requested,
                                            List<LocalEventImage> existing, LocalDateTime now) {
        List<LocalEventImage> planned = new ArrayList<>();
        for (int index = 0; index < requested.size(); index++) {
            NormalizedLocalEvent.Image wanted = requested.get(index);
            LocalEventImage.LocalEventImageBuilder row = LocalEventImage.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .position(index)
                    .credit(wanted.credit())
                    .createdAt(now)
                    .updatedAt(now);
            Optional<LocalEventImage> kept = existing.stream().filter(image -> matches(image, wanted.url())).findFirst();
            if (kept.isPresent()) {
                LocalEventImage previous = kept.get();
                row.sourceUrl(previous.getSourceUrl())
                        .url(previous.getUrl())
                        .status(previous.getStatus())
                        .attempts(previous.getAttempts())
                        .failureReason(previous.getFailureReason());
            } else if (isOwnStorage(wanted.url())) {
                row.sourceUrl(wanted.url()).url(wanted.url()).status(LocalEventImageStatus.DONE.name()).attempts(0);
            } else {
                row.sourceUrl(wanted.url()).status(LocalEventImageStatus.PENDING.name()).attempts(0);
            }
            planned.add(row.build());
        }
        return planned;
    }

    /** The cover: the position-0 image once it is re-hosted, else none (the job sets it later). */
    public String coverOf(List<LocalEventImage> images) {
        if (images.isEmpty()) {
            return null;
        }
        LocalEventImage first = images.get(0);
        return LocalEventImageStatus.DONE.name().equals(first.getStatus()) ? first.getUrl() : null;
    }

    /**
     * The most similar live event starting within one local day either side, within
     * {@value #DUPLICATE_RADIUS_KM} km or in the same province, with title similarity at least
     * {@value #DUPLICATE_MIN_SIMILARITY}.
     */
    public Optional<LocalEventDuplicateCandidate> findDuplicate(NormalizedLocalEvent normalized, UUID excludeId) {
        ZoneId zone = ZoneId.of(normalized.getTimezone());
        LocalDate day = LocalEventTimes.localDate(LocalEventTimes.fromUtc(normalized.getStartAt()), zone);
        double latitude = normalized.getLatitude().doubleValue();
        double longitude = normalized.getLongitude().doubleValue();
        double latDelta = DUPLICATE_RADIUS_KM / KM_PER_DEGREE;
        double lngDelta = DUPLICATE_RADIUS_KM / (KM_PER_DEGREE * Math.max(0.01, Math.cos(Math.toRadians(latitude))));
        LocalEventDuplicateSearchParams params = LocalEventDuplicateSearchParams.builder()
                .excludeId(excludeId)
                .titleNorm(normalized.getTitleNorm())
                .minSimilarity(DUPLICATE_MIN_SIMILARITY)
                .startFrom(LocalEventTimes.startOfDayUtc(day.minusDays(1), zone))
                .startTo(LocalEventTimes.startOfDayUtc(day.plusDays(2), zone))
                .latitude(latitude)
                .longitude(longitude)
                .radiusKm(DUPLICATE_RADIUS_KM)
                .minLat(latitude - latDelta)
                .maxLat(latitude + latDelta)
                .minLng(longitude - lngDelta)
                .maxLng(longitude + lngDelta)
                .provinceCode(normalized.getProvinceCode())
                .limit(1)
                .build();
        return repository.findDuplicateCandidates(params).stream().findFirst();
    }

    private boolean matches(LocalEventImage image, String url) {
        return url.equals(image.getSourceUrl()) || url.equals(image.getUrl());
    }

    private boolean isOwnStorage(String url) {
        return storageService.extractObjectKey(url) != null;
    }

    private String sessionsJson(List<NormalizedLocalEvent.Session> sessions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NormalizedLocalEvent.Session session : sessions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("startAt", session.startAt().format(UTC_TIME));
            row.put("endAt", session.endAt().format(UTC_TIME));
            row.put("label", session.label());
            row.put("status", session.status());
            rows.add(row);
        }
        return toJson(rows);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialise event field", exception);
        }
    }
}

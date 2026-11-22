package com.ds.goroute.localevent.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.domain.LocalEventImage;
import com.ds.goroute.localevent.dto.AdminLocalEventDetailResponse;
import com.ds.goroute.localevent.dto.AdminLocalEventRow;
import com.ds.goroute.localevent.dto.LocalEventFieldError;
import com.ds.goroute.localevent.dto.LocalEventPublishStatusRequest;
import com.ds.goroute.localevent.dto.LocalEventUpsertRequest;
import com.ds.goroute.localevent.persistence.LocalEventAdminSearchParams;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.type.LocalEventCategory;
import com.ds.goroute.type.LocalEventPublishStatus;
import com.ds.goroute.type.LocalEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The console side of local events (/v1/api/admin/events). Admins are the only authors: they
 * create and edit events here or import them through the crawler API. An edit of an imported
 * event marks it {@code manually_edited}, which makes later re-imports skip it.
 */
@Service
@RequiredArgsConstructor
public class LocalEventAdminService {

    /** Source recorded for events created in the console. */
    public static final String ADMIN_SOURCE = "admin";
    /** Console uploads land here; the image job stores crawled images under events/<eventId>/. */
    static final String UPLOAD_PREFIX = "events/uploads";
    private static final Set<String> SORT_KEYS = Set.of("startAt", "createdAt", "updatedAt");

    private final LocalEventRepository repository;
    private final LocalEventNormalizer normalizer;
    private final LocalEventAssembler assembler;
    private final LocalEventResponses responses;
    private final FileUploadService fileUploadService;

    Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public PageResponse<AdminLocalEventRow> list(String search, String publishStatus, String eventStatus,
                                                 String category, String source, String provinceCode,
                                                 String startFrom, String startTo, boolean duplicatesOnly,
                                                 String sort, String direction, int page, int size) {
        String sortKey = sort == null || sort.isBlank() ? "updatedAt" : sort.trim();
        if (!SORT_KEYS.contains(sortKey)) {
            throw invalid("sort must be one of startAt, createdAt, updatedAt");
        }
        String order = direction == null || direction.isBlank() ? "desc" : direction.trim().toLowerCase(Locale.ROOT);
        if (!order.equals("asc") && !order.equals("desc")) {
            throw invalid("direction must be asc or desc");
        }
        String trimmedSearch = search == null || search.isBlank() ? null : search.trim();
        LocalEventAdminSearchParams params = LocalEventAdminSearchParams.builder()
                .search(trimmedSearch)
                .searchNorm(trimmedSearch == null ? null
                        : LocalEventText.escapeLike(LocalEventText.normalizeTitle(trimmedSearch)))
                .publishStatuses(codes("publishStatus", publishStatus, LocalEventPublishStatus.class))
                .eventStatuses(codes("eventStatus", eventStatus, LocalEventStatus.class))
                .categories(codes("category", category, LocalEventCategory.class))
                .source(source == null || source.isBlank() ? null : source.trim())
                .provinceCode(provinceCode == null || provinceCode.isBlank() ? null : provinceCode.trim())
                .startFrom(bound("startFrom", startFrom, false))
                .startTo(bound("startTo", startTo, true))
                .duplicatesOnly(duplicatesOnly)
                .sortKey(sortKey)
                .descending(order.equals("desc"))
                .limit(size)
                .offset(page * size)
                .build();
        List<LocalEvent> events = repository.findForAdmin(params);
        Map<UUID, Integer> pending = repository.countPendingImages(
                events.stream().map(LocalEvent::getId).collect(Collectors.toList()));
        List<AdminLocalEventRow> rows = events.stream()
                .map(event -> responses.adminRow(event, pending.getOrDefault(event.getId(), 0)))
                .toList();
        return PageResponse.of(rows, repository.countForAdmin(params), page, size);
    }

    @Transactional(readOnly = true)
    public AdminLocalEventDetailResponse detail(UUID eventId) {
        return toDetail(requireLive(eventId));
    }

    /** The crawler's read-back by natural key. Also answers for a deleted event ({@code deleted: true}). */
    @Transactional(readOnly = true)
    public AdminLocalEventDetailResponse lookup(String source, String externalId) {
        if (source == null || source.isBlank() || externalId == null || externalId.isBlank()) {
            throw invalid("source and externalId are required");
        }
        LocalEvent event = repository.findBySourceAndExternalId(source.trim(), externalId.trim())
                .orElseThrow(LocalEventAdminService::notFound);
        return toDetail(event);
    }

    @Transactional
    public AdminLocalEventDetailResponse create(LocalEventUpsertRequest request, UUID actorUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        NormalizedLocalEvent normalized = requireValid(
                normalizer.normalize(request, LocalEventNormalizer.Mode.ADMIN_CREATE, now));
        UUID eventId = UUID.randomUUID();
        LocalEvent event = assembler.newEvent(normalized, eventId, ADMIN_SOURCE, actorUserId, now);
        List<LocalEventImage> images = assembler.planImages(eventId, normalized.getImages(), List.of(), now);
        event.setCoverUrl(assembler.coverOf(images));
        repository.insert(event);
        repository.replaceImages(eventId, images);
        return toDetail(repository.findById(eventId).orElseThrow(LocalEventAdminService::notFound));
    }

    /**
     * Saves a console edit. The publish status in the body is ignored (use publish-status). An
     * imported event becomes {@code manually_edited}, so the crawler no longer overwrites it.
     */
    @Transactional
    public AdminLocalEventDetailResponse update(UUID eventId, LocalEventUpsertRequest request, UUID actorUserId) {
        if (request.getExpectedVersion() == null) {
            throw invalid("expectedVersion is required");
        }
        LocalEvent event = requireLive(eventId);
        if (!request.getExpectedVersion().equals(event.getDataVersion())) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The event was changed by someone else; reload it");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        NormalizedLocalEvent normalized = requireValid(
                normalizer.normalize(request, LocalEventNormalizer.Mode.ADMIN_UPDATE, now));
        assembler.applyContent(event, normalized, actorUserId, now);
        if (!ADMIN_SOURCE.equals(event.getSource())) {
            event.setManuallyEdited(true);
        }
        List<LocalEventImage> stored = repository.findImagesByEvent(eventId);
        List<LocalEventImage> images = stored;
        if (assembler.imagesChanged(normalized.getImages(), stored)) {
            images = assembler.planImages(eventId, normalized.getImages(), stored, now);
            repository.replaceImages(eventId, images);
        }
        event.setCoverUrl(assembler.coverOf(images));
        if (!repository.updateContent(event, request.getExpectedVersion())) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The event was changed by someone else; reload it");
        }
        return toDetail(repository.findById(eventId).orElseThrow(LocalEventAdminService::notFound));
    }

    /** DRAFT/PUBLISHED/HIDDEN; {@code published_at} is stamped the first time it becomes PUBLISHED. */
    @Transactional
    public AdminLocalEventDetailResponse changePublishStatus(UUID eventId, LocalEventPublishStatusRequest request,
                                                             UUID actorUserId) {
        requireLive(eventId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime publishedAt = request.getPublishStatus() == LocalEventPublishStatus.PUBLISHED ? now : null;
        if (!repository.updatePublishStatus(eventId, request.getExpectedVersion(), request.getPublishStatus().name(),
                publishedAt, actorUserId, now)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The event was changed by someone else; reload it");
        }
        return toDetail(repository.findById(eventId).orElseThrow(LocalEventAdminService::notFound));
    }

    /** Soft delete. A later import of the same (source, externalId) answers SKIPPED_DELETED. */
    @Transactional
    public void delete(UUID eventId, UUID actorUserId) {
        requireLive(eventId);
        if (!repository.softDelete(eventId, actorUserId, LocalDateTime.now(clock))) {
            throw notFound();
        }
    }

    /** FAILED images of the event go back to PENDING with their attempts reset. */
    @Transactional
    public AdminLocalEventDetailResponse retryImages(UUID eventId) {
        requireLive(eventId);
        repository.retryFailedImages(eventId, LocalDateTime.now(clock));
        return toDetail(repository.findById(eventId).orElseThrow(LocalEventAdminService::notFound));
    }

    /** Console image upload through the single upload door (size/type check and image moderation). */
    public String uploadImage(MultipartFile file, UUID actorUserId) {
        ImageUploadOutcome outcome = fileUploadService.uploadImage(ImageUploadRequest.of(
                actorUserId, ImageUploadRequest.ImageEntryPoint.LOCAL_EVENT, UPLOAD_PREFIX), file);
        if (!outcome.isAccepted()) {
            throw new BusinessException(
                    outcome.isContentRejection() ? ErrorConstant.IMAGE_REJECTED_BY_MODERATION : ErrorConstant.INVALID_PARAMETERS,
                    outcome.failureMessage());
        }
        return outcome.url();
    }

    private AdminLocalEventDetailResponse toDetail(LocalEvent event) {
        LocalEvent duplicateOf = event.getDuplicateOfEventId() == null
                ? null
                : repository.findById(event.getDuplicateOfEventId()).orElse(null);
        return responses.adminDetail(event, repository.findImagesByEvent(event.getId()), duplicateOf);
    }

    private LocalEvent requireLive(UUID eventId) {
        return repository.findById(eventId)
                .filter(event -> !Boolean.TRUE.equals(event.getIsDeleted()))
                .orElseThrow(LocalEventAdminService::notFound);
    }

    private static NormalizedLocalEvent requireValid(NormalizedLocalEvent normalized) {
        if (normalized.isValid()) {
            return normalized;
        }
        String details = normalized.getErrors().stream()
                .map(LocalEventAdminService::describe)
                .collect(Collectors.joining("; "));
        throw new BusinessException(ErrorConstant.LOCAL_EVENT_INVALID, details);
    }

    private static String describe(LocalEventFieldError error) {
        return error.getField() + ": " + error.getCode() + " (" + error.getMessage() + ")";
    }

    private static <E extends Enum<E>> List<String> codes(String name, String value, Class<E> type) {
        List<String> codes = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return codes;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String code = part.trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            try {
                Enum.valueOf(type, code);
            } catch (IllegalArgumentException exception) {
                throw invalid("Unknown " + name + " " + code);
            }
            if (seen.add(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    /**
     * A list bound: an ISO instant with offset, or a date (YYYY-MM-DD) read as a Vietnamese day —
     * the start of that day for {@code startFrom}, the start of the next day for {@code startTo}
     * (so the day itself is included).
     */
    private static LocalDateTime bound(String name, String value, boolean upper) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Optional<Instant> instant = LocalEventTimes.parseInstant(value);
        if (instant.isPresent()) {
            return LocalEventTimes.toUtc(instant.get());
        }
        Optional<LocalDate> date = LocalEventTimes.parseDate(value);
        if (date.isPresent()) {
            return LocalEventTimes.startOfDayUtc(upper ? date.get().plusDays(1) : date.get(), LocalEventTimes.VIETNAM);
        }
        throw invalid(name + " must be a date (YYYY-MM-DD) or ISO-8601 with an offset");
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.LOCAL_EVENT_NOT_FOUND, "Event not found");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}

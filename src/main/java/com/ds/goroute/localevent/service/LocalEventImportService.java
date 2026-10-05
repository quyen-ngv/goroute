package com.ds.goroute.localevent.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEventImportBatch;
import com.ds.goroute.localevent.domain.LocalEventImportItem;
import com.ds.goroute.localevent.dto.LocalEventFieldError;
import com.ds.goroute.localevent.dto.LocalEventImportItemRequest;
import com.ds.goroute.localevent.dto.LocalEventImportItemResult;
import com.ds.goroute.localevent.dto.LocalEventImportRequest;
import com.ds.goroute.localevent.dto.LocalEventImportResponse;
import com.ds.goroute.localevent.persistence.LocalEventImportRepository;
import com.ds.goroute.type.LocalEventFieldErrorCode;
import com.ds.goroute.type.LocalEventImportResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * POST /v1/api/admin/events/import (SPEC_V2 §1.5). Reads each item on its own, validates it,
 * refuses repeats of the same (source, externalId) within the batch, then hands each valid item
 * to {@link LocalEventImportItemProcessor}, which runs it in its own transaction. A non-dry run
 * stores one batch row and one row per item (who sent it, through which API key, what happened).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalEventImportService {

    /** Most items one call may carry. */
    public static final int MAX_ITEMS = 100;

    private final LocalEventNormalizer normalizer;
    private final LocalEventImportItemProcessor processor;
    private final LocalEventImportRepository importRepository;
    private final ObjectMapper objectMapper;

    Clock clock = Clock.systemUTC();

    public LocalEventImportResponse importEvents(LocalEventImportRequest request, UUID actorUserId, UUID apiKeyId) {
        List<JsonNode> events = request == null ? null : request.getEvents();
        if (events == null || events.isEmpty() || events.size() > MAX_ITEMS) {
            throw new BusinessException(ErrorConstant.LOCAL_EVENT_IMPORT_INVALID,
                    "events must hold between 1 and " + MAX_ITEMS + " items");
        }
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        LocalDateTime now = LocalDateTime.now(clock);

        List<LocalEventImportItemResult> results = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (int index = 0; index < events.size(); index++) {
            results.add(importOne(index, events.get(index), seenKeys, actorUserId, dryRun, now));
        }

        LocalEventImportResponse response = summarise(results, dryRun);
        if (!dryRun) {
            UUID batchId = UUID.randomUUID();
            importRepository.insertBatch(batchRow(batchId, response, actorUserId, apiKeyId, now),
                    itemRows(batchId, results, now));
            response.setBatchId(batchId);
        }
        return response;
    }

    private LocalEventImportItemResult importOne(int index, JsonNode node, Set<String> seenKeys, UUID actorUserId,
                                                 boolean dryRun, LocalDateTime now) {
        if (node == null || !node.isObject()) {
            return failed(index, null, null, List.of(LocalEventFieldError.of(
                    "events[" + index + "]", LocalEventFieldErrorCode.INVALID_FORMAT, "Each event must be a JSON object")));
        }
        LocalEventImportItemRequest item;
        try {
            item = objectMapper.treeToValue(node, LocalEventImportItemRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return failed(index, text(node, "source"), text(node, "externalId"), List.of(LocalEventFieldError.of(
                    fieldPath(exception), LocalEventFieldErrorCode.INVALID_FORMAT, "A field has the wrong type")));
        }

        String source = trimmed(item.getSource());
        String externalId = trimmed(item.getExternalId());
        if (source != null && externalId != null && !seenKeys.add(source + '\u0000' + externalId)) {
            return failed(index, source, externalId, List.of(LocalEventFieldError.of("externalId",
                    LocalEventFieldErrorCode.DUPLICATE_IN_BATCH,
                    "This (source, externalId) already appears earlier in the same batch")));
        }

        NormalizedLocalEvent normalized = normalizer.normalize(item, LocalEventNormalizer.Mode.IMPORT, now);
        if (!normalized.isValid()) {
            return failed(index, source, externalId, normalized.getErrors());
        }
        try {
            return processor.process(index, normalized, actorUserId, dryRun, now);
        } catch (RuntimeException exception) {
            log.warn("Event import item {} ({}:{}) could not be saved: {}", index, source, externalId,
                    exception.getClass().getSimpleName());
            return failed(index, source, externalId, List.of(LocalEventFieldError.of(null,
                    LocalEventFieldErrorCode.INTERNAL_ERROR, "The event could not be saved; send it again")));
        }
    }

    static LocalEventImportResponse summarise(List<LocalEventImportItemResult> results, boolean dryRun) {
        int created = count(results, LocalEventImportResult.CREATED);
        int updated = count(results, LocalEventImportResult.UPDATED);
        int unchanged = count(results, LocalEventImportResult.UNCHANGED);
        int skipped = count(results, LocalEventImportResult.SKIPPED_EDITED)
                + count(results, LocalEventImportResult.SKIPPED_DELETED);
        int failed = count(results, LocalEventImportResult.FAILED);
        return LocalEventImportResponse.builder()
                .dryRun(dryRun)
                .total(results.size())
                .created(created)
                .updated(updated)
                .unchanged(unchanged)
                .skipped(skipped)
                .failed(failed)
                .items(results)
                .build();
    }

    private static int count(List<LocalEventImportItemResult> results, LocalEventImportResult result) {
        return (int) results.stream().filter(item -> item.getResult() == result).count();
    }

    private LocalEventImportBatch batchRow(UUID batchId, LocalEventImportResponse response, UUID actorUserId,
                                           UUID apiKeyId, LocalDateTime now) {
        return LocalEventImportBatch.builder()
                .id(batchId)
                .actorUserId(actorUserId)
                .apiKeyId(apiKeyId)
                .dryRun(false)
                .total(response.getTotal())
                .created(response.getCreated())
                .updated(response.getUpdated())
                .unchanged(response.getUnchanged())
                .skipped(response.getSkipped())
                .failed(response.getFailed())
                .createdAt(now)
                .build();
    }

    private List<LocalEventImportItem> itemRows(UUID batchId, List<LocalEventImportItemResult> results,
                                                LocalDateTime now) {
        return results.stream()
                .map(result -> LocalEventImportItem.builder()
                        .id(UUID.randomUUID())
                        .batchId(batchId)
                        .itemIndex(result.getIndex())
                        .source(clip(result.getSource(), 50))
                        .externalId(clip(result.getExternalId(), 255))
                        .result(result.getResult().name())
                        .eventId(result.getEventId())
                        .duplicateOfEventId(result.getDuplicateOfEventId())
                        .errors(errorsJson(result.getErrors()))
                        .createdAt(now)
                        .build())
                .collect(Collectors.toList());
    }

    private String errorsJson(List<LocalEventFieldError> errors) {
        try {
            return objectMapper.writeValueAsString(errors == null ? List.of() : errors);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private static LocalEventImportItemResult failed(int index, String source, String externalId,
                                                     List<LocalEventFieldError> errors) {
        return LocalEventImportItemResult.builder()
                .index(index)
                .source(source)
                .externalId(externalId)
                .result(LocalEventImportResult.FAILED)
                .errors(errors)
                .build();
    }

    /** "venue.latitude" for an unreadable nested field, or null when the path is unknown. */
    private static String fieldPath(Exception exception) {
        Throwable cause = exception;
        while (cause != null && !(cause instanceof JsonMappingException)) {
            cause = cause.getCause();
        }
        if (!(cause instanceof JsonMappingException mapping) || mapping.getPath().isEmpty()) {
            return null;
        }
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : mapping.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? clip(value.asText().trim(), 255) : null;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

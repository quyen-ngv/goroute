package com.ds.goroute.localevent.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.domain.LocalEventDuplicateCandidate;
import com.ds.goroute.localevent.domain.LocalEventImage;
import com.ds.goroute.localevent.dto.LocalEventImportItemResult;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.type.LocalEventImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies ONE valid import item, in its own transaction (called through the Spring proxy by
 * {@link LocalEventImportService}, so one failing item never rolls back the others).
 *
 * <p>Decision order (SPEC_V2 §1.5): unknown key → CREATED (with the fuzzy duplicate check);
 * deleted → SKIPPED_DELETED; console-edited and not {@code overwriteManualEdits} → SKIPPED_EDITED;
 * same content hash, or {@code sourceUpdatedAt} older than stored → UNCHANGED; otherwise UPDATED.
 * Publish status is set on create only. A dry run takes the same decisions and writes nothing.
 */
@Service
@RequiredArgsConstructor
public class LocalEventImportItemProcessor {

    private final LocalEventRepository repository;
    private final LocalEventAssembler assembler;

    @Transactional
    public LocalEventImportItemResult process(int index, NormalizedLocalEvent item, UUID actorUserId,
                                              boolean dryRun, LocalDateTime now) {
        Optional<LocalEvent> found = repository.findBySourceAndExternalId(item.getSource(), item.getExternalId());
        if (found.isEmpty()) {
            return create(index, item, actorUserId, dryRun, now);
        }
        LocalEvent existing = found.get();
        if (Boolean.TRUE.equals(existing.getIsDeleted())) {
            return result(index, item, LocalEventImportResult.SKIPPED_DELETED, existing.getId(), null);
        }
        if (Boolean.TRUE.equals(existing.getManuallyEdited()) && !item.isOverwriteManualEdits()) {
            return result(index, item, LocalEventImportResult.SKIPPED_EDITED, existing.getId(), null);
        }
        if (Objects.equals(existing.getContentHash(), item.getContentHash())) {
            return result(index, item, LocalEventImportResult.UNCHANGED, existing.getId(), null);
        }
        if (item.getSourceUpdatedAt() != null && existing.getSourceUpdatedAt() != null
                && item.getSourceUpdatedAt().isBefore(existing.getSourceUpdatedAt())) {
            return result(index, item, LocalEventImportResult.UNCHANGED, existing.getId(), null);
        }
        if (!dryRun) {
            update(existing, item, actorUserId, now);
        }
        return result(index, item, LocalEventImportResult.UPDATED, existing.getId(), null);
    }

    private LocalEventImportItemResult create(int index, NormalizedLocalEvent item, UUID actorUserId,
                                              boolean dryRun, LocalDateTime now) {
        UUID duplicateOf = assembler.findDuplicate(item, null).map(LocalEventDuplicateCandidate::getId).orElse(null);
        if (dryRun) {
            return result(index, item, LocalEventImportResult.CREATED, null, duplicateOf);
        }
        UUID eventId = UUID.randomUUID();
        LocalEvent event = assembler.newEvent(item, eventId, item.getSource(), actorUserId, now);
        assembler.applyImportMetadata(event, item);
        event.setDuplicateOfEventId(duplicateOf);
        List<LocalEventImage> images = assembler.planImages(eventId, item.getImages(), List.of(), now);
        event.setCoverUrl(assembler.coverOf(images));
        repository.insert(event);
        repository.replaceImages(eventId, images);
        return result(index, item, LocalEventImportResult.CREATED, eventId, duplicateOf);
    }

    private void update(LocalEvent existing, NormalizedLocalEvent item, UUID actorUserId, LocalDateTime now) {
        long expectedVersion = existing.getDataVersion() == null ? 0L : existing.getDataVersion();
        assembler.applyContent(existing, item, actorUserId, now);
        assembler.applyImportMetadata(existing, item);
        // A successful overwrite hands the event back to the crawler.
        existing.setManuallyEdited(false);
        List<LocalEventImage> stored = repository.findImagesByEvent(existing.getId());
        List<LocalEventImage> images = stored;
        if (assembler.imagesChanged(item.getImages(), stored)) {
            images = assembler.planImages(existing.getId(), item.getImages(), stored, now);
            repository.replaceImages(existing.getId(), images);
        }
        existing.setCoverUrl(assembler.coverOf(images));
        if (!repository.updateContent(existing, expectedVersion)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The event changed while it was being imported");
        }
    }

    private LocalEventImportItemResult result(int index, NormalizedLocalEvent item, LocalEventImportResult result,
                                              UUID eventId, UUID duplicateOf) {
        return LocalEventImportItemResult.builder()
                .index(index)
                .source(item.getSource())
                .externalId(item.getExternalId())
                .result(result)
                .eventId(eventId)
                .duplicateOfEventId(duplicateOf)
                .errors(List.of())
                .build();
    }
}

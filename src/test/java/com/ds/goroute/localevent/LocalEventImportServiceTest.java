package com.ds.goroute.localevent;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.domain.LocalEventDuplicateCandidate;
import com.ds.goroute.localevent.domain.LocalEventImage;
import com.ds.goroute.localevent.domain.LocalEventImportItem;
import com.ds.goroute.localevent.dto.LocalEventImportItemRequest;
import com.ds.goroute.localevent.dto.LocalEventImportItemResult;
import com.ds.goroute.localevent.dto.LocalEventImportRequest;
import com.ds.goroute.localevent.dto.LocalEventImportResponse;
import com.ds.goroute.localevent.persistence.LocalEventImportRepository;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.localevent.service.LocalEventAssembler;
import com.ds.goroute.localevent.service.LocalEventImportItemProcessor;
import com.ds.goroute.localevent.service.LocalEventImportService;
import com.ds.goroute.localevent.service.LocalEventNormalizer;
import com.ds.goroute.localevent.service.NormalizedLocalEvent;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.LocalEventFieldErrorCode;
import com.ds.goroute.type.LocalEventImportResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ds.goroute.localevent.LocalEventTestData.NOW;
import static com.ds.goroute.localevent.LocalEventTestData.concert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The import end to end with a real normaliser, assembler and item processor over mocked
 * persistence: every result kind, the validation failures, dry runs and the audit rows.
 */
class LocalEventImportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final LocalEventRepository repository = mock(LocalEventRepository.class);
    private final LocalEventImportRepository importRepository = mock(LocalEventImportRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final UUID actor = UUID.randomUUID();
    private final UUID apiKey = UUID.randomUUID();
    private LocalEventImportService service;

    @BeforeEach
    void setUp() throws Exception {
        LocalEventNormalizer normalizer = new LocalEventNormalizer(LocalEventTestData.geoEverywhere(), objectMapper);
        LocalEventAssembler assembler = new LocalEventAssembler(repository, storage, objectMapper);
        LocalEventImportItemProcessor processor = new LocalEventImportItemProcessor(repository, assembler);
        service = new LocalEventImportService(normalizer, processor, importRepository, objectMapper);
        Field clock = LocalEventImportService.class.getDeclaredField("clock");
        clock.setAccessible(true);
        clock.set(service, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        when(repository.findBySourceAndExternalId(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findDuplicateCandidates(any())).thenReturn(List.of());
        when(repository.updateContent(any(), anyLong())).thenReturn(true);
    }

    // --- result kinds ------------------------------------------------------------------------

    @Test
    void anUnknownEventIsCreatedAsADraftWithItsImageQueued() {
        LocalEventImportResponse response = service.importEvents(request(false, concert()), actor, apiKey);

        assertThat(response.getCreated()).isEqualTo(1);
        LocalEventImportItemResult item = response.getItems().get(0);
        assertThat(item.getResult()).isEqualTo(LocalEventImportResult.CREATED);
        assertThat(item.getEventId()).isNotNull();

        ArgumentCaptor<LocalEvent> inserted = ArgumentCaptor.forClass(LocalEvent.class);
        verify(repository).insert(inserted.capture());
        assertThat(inserted.getValue().getPublishStatus()).isEqualTo("DRAFT");
        assertThat(inserted.getValue().getPublishedAt()).isNull();
        assertThat(inserted.getValue().getSource()).isEqualTo("ticketbox.vn");
        assertThat(inserted.getValue().getCreatedBy()).isEqualTo(actor);
        assertThat(inserted.getValue().getCoverUrl()).isNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LocalEventImage>> images = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceImages(eq(item.getEventId()), images.capture());
        assertThat(images.getValue()).singleElement()
                .satisfies(image -> {
                    assertThat(image.getStatus()).isEqualTo("PENDING");
                    assertThat(image.getSourceUrl()).isEqualTo("https://images.example.com/a.jpg");
                    assertThat(image.getUrl()).isNull();
                });
    }

    @Test
    void aRequestedPublishedStatusIsAppliedOnCreate() {
        LocalEventImportItemRequest published = concert();
        published.setPublishStatus("PUBLISHED");

        service.importEvents(request(false, published), actor, apiKey);

        ArgumentCaptor<LocalEvent> inserted = ArgumentCaptor.forClass(LocalEvent.class);
        verify(repository).insert(inserted.capture());
        assertThat(inserted.getValue().getPublishStatus()).isEqualTo("PUBLISHED");
        assertThat(inserted.getValue().getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void aLikelyDuplicateIsStillCreatedAndReported() {
        UUID other = UUID.randomUUID();
        when(repository.findDuplicateCandidates(any())).thenReturn(List.of(
                LocalEventDuplicateCandidate.builder().id(other).score(0.8).build()));

        LocalEventImportItemResult item = service.importEvents(request(false, concert()), actor, apiKey).getItems().get(0);

        assertThat(item.getResult()).isEqualTo(LocalEventImportResult.CREATED);
        assertThat(item.getDuplicateOfEventId()).isEqualTo(other);
        ArgumentCaptor<LocalEvent> inserted = ArgumentCaptor.forClass(LocalEvent.class);
        verify(repository).insert(inserted.capture());
        assertThat(inserted.getValue().getDuplicateOfEventId()).isEqualTo(other);
    }

    @Test
    void sameContentIsUnchanged() {
        LocalEvent stored = storedFor(concert());
        when(repository.findBySourceAndExternalId("ticketbox.vn", "evt-123")).thenReturn(Optional.of(stored));

        LocalEventImportItemResult item = service.importEvents(request(false, concert()), actor, apiKey).getItems().get(0);

        assertThat(item.getResult()).isEqualTo(LocalEventImportResult.UNCHANGED);
        assertThat(item.getEventId()).isEqualTo(stored.getId());
        verify(repository, never()).updateContent(any(), anyLong());
    }

    @Test
    void changedContentIsUpdatedButThePublishStatusIsKept() {
        LocalEvent stored = storedFor(concert());
        stored.setPublishStatus("HIDDEN");
        when(repository.findBySourceAndExternalId("ticketbox.vn", "evt-123")).thenReturn(Optional.of(stored));
        when(repository.findImagesByEvent(stored.getId())).thenReturn(List.of());
        LocalEventImportItemRequest changed = concert();
        changed.setPriceMax(1500000L);
        changed.setPublishStatus("PUBLISHED");

        LocalEventImportItemResult item = service.importEvents(request(false, changed), actor, apiKey).getItems().get(0);

        assertThat(item.getResult()).isEqualTo(LocalEventImportResult.UPDATED);
        ArgumentCaptor<LocalEvent> updated = ArgumentCaptor.forClass(LocalEvent.class);
        verify(repository).updateContent(updated.capture(), eq(4L));
        assertThat(updated.getValue().getPriceMax()).isEqualTo(1500000L);
        assertThat(updated.getValue().getPublishStatus()).isEqualTo("HIDDEN");
        assertThat(updated.getValue().getUpdatedBy()).isEqualTo(actor);
    }

    @Test
    void anOlderSourceVersionIsUnchanged() {
        LocalEvent stored = storedFor(concert());
        stored.setSourceUpdatedAt(LocalDateTime.of(2026, 10, 3, 0, 0));
        when(repository.findBySourceAndExternalId("ticketbox.vn", "evt-123")).thenReturn(Optional.of(stored));
        LocalEventImportItemRequest older = concert();
        older.setPriceMax(1500000L);
        older.setSourceUpdatedAt("2026-10-02T00:00:00Z");

        LocalEventImportItemResult item = service.importEvents(request(false, older), actor, apiKey).getItems().get(0);

        assertThat(item.getResult()).isEqualTo(LocalEventImportResult.UNCHANGED);
        verify(repository, never()).updateContent(any(), anyLong());
    }

    @Test
    void aConsoleEditedEventIsSkippedUnlessTheItemAsksToOverwrite() {
        LocalEvent stored = storedFor(concert());
        stored.setManuallyEdited(true);
        stored.setContentHash("edited-in-console");
        when(repository.findBySourceAndExternalId("ticketbox.vn", "evt-123")).thenReturn(Optional.of(stored));
        when(repository.findImagesByEvent(stored.getId())).thenReturn(List.of());

        LocalEventImportItemResult skipped = service.importEvents(request(false, concert()), actor, apiKey)
                .getItems().get(0);
        assertThat(skipped.getResult()).isEqualTo(LocalEventImportResult.SKIPPED_EDITED);
        verify(repository, never()).updateContent(any(), anyLong());

        LocalEventImportItemRequest overwrite = concert();
        overwrite.setOverwriteManualEdits(true);
        LocalEventImportItemResult overwritten = service.importEvents(request(false, overwrite), actor, apiKey)
                .getItems().get(0);
        assertThat(overwritten.getResult()).isEqualTo(LocalEventImportResult.UPDATED);
        ArgumentCaptor<LocalEvent> updated = ArgumentCaptor.forClass(LocalEvent.class);
        verify(repository).updateContent(updated.capture(), anyLong());
        assertThat(updated.getValue().getManuallyEdited()).isFalse();
    }

    @Test
    void aDeletedEventIsNeverRevived() {
        LocalEvent stored = storedFor(concert());
        stored.setIsDeleted(true);
        stored.setContentHash("old");
        when(repository.findBySourceAndExternalId("ticketbox.vn", "evt-123")).thenReturn(Optional.of(stored));

        LocalEventImportItemResult item = service.importEvents(request(false, concert()), actor, apiKey).getItems().get(0);

        assertThat(item.getResult()).isEqualTo(LocalEventImportResult.SKIPPED_DELETED);
        verify(repository, never()).updateContent(any(), anyLong());
        verify(repository, never()).insert(any());
    }

    // --- failures --------------------------------------------------------------------------

    @Test
    void anInvalidItemFailsAloneWithItsFieldErrors() {
        LocalEventImportItemRequest invalid = concert();
        invalid.setExternalId("evt-999");
        invalid.setCategory("CIRCUS");
        invalid.setStartAt("2026-11-07T19:30:00");

        LocalEventImportResponse response = service.importEvents(request(false, concert(), invalid), actor, apiKey);

        assertThat(response.getCreated()).isEqualTo(1);
        assertThat(response.getFailed()).isEqualTo(1);
        LocalEventImportItemResult failed = response.getItems().get(1);
        assertThat(failed.getResult()).isEqualTo(LocalEventImportResult.FAILED);
        assertThat(failed.getExternalId()).isEqualTo("evt-999");
        assertThat(failed.getErrors()).extracting(error -> error.getField() + ":" + error.getCode())
                .contains("category:INVALID_ENUM", "startAt:INVALID_FORMAT");
    }

    @Test
    void aRepeatedKeyInTheSameBatchFails() {
        LocalEventImportResponse response = service.importEvents(request(false, concert(), concert()), actor, apiKey);

        assertThat(response.getItems()).extracting(LocalEventImportItemResult::getResult)
                .containsExactly(LocalEventImportResult.CREATED, LocalEventImportResult.FAILED);
        assertThat(response.getItems().get(1).getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(LocalEventFieldErrorCode.DUPLICATE_IN_BATCH));
    }

    @Test
    void aFieldOfTheWrongTypeFailsThatItemWithItsPath() {
        ObjectNode node = objectMapper.valueToTree(concert());
        ((ObjectNode) node.get("venue")).put("latitude", "north");

        LocalEventImportItemResult item = service.importEvents(
                LocalEventImportRequest.builder().events(List.of(node)).build(), actor, apiKey).getItems().get(0);

        assertThat(item.getResult()).isEqualTo(LocalEventImportResult.FAILED);
        assertThat(item.getSource()).isEqualTo("ticketbox.vn");
        assertThat(item.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getField()).isEqualTo("venue.latitude");
            assertThat(error.getCode()).isEqualTo(LocalEventFieldErrorCode.INVALID_FORMAT);
        });
    }

    @Test
    void aSaveConflictFailsOnlyThatItem() {
        LocalEvent stored = storedFor(concert());
        stored.setContentHash("old");
        when(repository.findBySourceAndExternalId("ticketbox.vn", "evt-123")).thenReturn(Optional.of(stored));
        when(repository.findImagesByEvent(stored.getId())).thenReturn(List.of());
        when(repository.updateContent(any(), anyLong())).thenReturn(false);

        LocalEventImportItemResult item = service.importEvents(request(false, concert()), actor, apiKey).getItems().get(0);

        assertThat(item.getResult()).isEqualTo(LocalEventImportResult.FAILED);
        assertThat(item.getErrors().get(0).getCode()).isEqualTo(LocalEventFieldErrorCode.INTERNAL_ERROR);
    }

    @Test
    void anEmptyOrOversizedBatchIsRefusedAsAWhole() {
        assertThatThrownBy(() -> service.importEvents(LocalEventImportRequest.builder().events(List.of()).build(),
                actor, apiKey))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.LOCAL_EVENT_IMPORT_INVALID));
        List<JsonNode> tooMany = new ArrayList<>(Collections.nCopies(101, objectMapper.valueToTree(concert())));
        assertThatThrownBy(() -> service.importEvents(LocalEventImportRequest.builder().events(tooMany).build(),
                actor, apiKey))
                .isInstanceOf(BusinessException.class);
    }

    // --- dry run and audit -------------------------------------------------------------------

    @Test
    void aDryRunComputesResultsAndWritesNothing() {
        LocalEvent stored = storedFor(concert());
        stored.setContentHash("old");
        LocalEventImportItemRequest other = concert();
        other.setExternalId("evt-456");
        when(repository.findBySourceAndExternalId("ticketbox.vn", "evt-123")).thenReturn(Optional.of(stored));

        LocalEventImportResponse response = service.importEvents(request(true, concert(), other), actor, apiKey);

        assertThat(response.isDryRun()).isTrue();
        assertThat(response.getBatchId()).isNull();
        assertThat(response.getItems()).extracting(LocalEventImportItemResult::getResult)
                .containsExactly(LocalEventImportResult.UPDATED, LocalEventImportResult.CREATED);
        assertThat(response.getItems().get(1).getEventId()).isNull();
        verify(repository, never()).insert(any());
        verify(repository, never()).updateContent(any(), anyLong());
        verify(repository, never()).replaceImages(any(), any());
        verify(importRepository, never()).insertBatch(any(), any());
    }

    @Test
    void aRealRunStoresTheBatchAndOneRowPerItem() {
        LocalEventImportItemRequest invalid = concert();
        invalid.setExternalId("evt-999");
        invalid.setTitle(null);

        LocalEventImportResponse response = service.importEvents(request(false, concert(), invalid), actor, apiKey);

        assertThat(response.getBatchId()).isNotNull();
        ArgumentCaptor<com.ds.goroute.localevent.domain.LocalEventImportBatch> batch =
                ArgumentCaptor.forClass(com.ds.goroute.localevent.domain.LocalEventImportBatch.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LocalEventImportItem>> items = ArgumentCaptor.forClass(List.class);
        verify(importRepository).insertBatch(batch.capture(), items.capture());
        assertThat(batch.getValue().getActorUserId()).isEqualTo(actor);
        assertThat(batch.getValue().getApiKeyId()).isEqualTo(apiKey);
        assertThat(batch.getValue().getTotal()).isEqualTo(2);
        assertThat(batch.getValue().getCreated()).isEqualTo(1);
        assertThat(batch.getValue().getFailed()).isEqualTo(1);
        assertThat(items.getValue()).extracting(LocalEventImportItem::getResult).containsExactly("CREATED", "FAILED");
        assertThat(items.getValue().get(1).getErrors()).contains("REQUIRED");
    }

    // --- helpers -----------------------------------------------------------------------------

    private LocalEventImportRequest request(boolean dryRun, LocalEventImportItemRequest... items) {
        List<JsonNode> nodes = new ArrayList<>();
        for (LocalEventImportItemRequest item : items) {
            nodes.add(objectMapper.valueToTree(item));
        }
        return LocalEventImportRequest.builder().dryRun(dryRun).events(nodes).build();
    }

    /** A stored event whose content hash is exactly what {@code item} normalises to. */
    private LocalEvent storedFor(LocalEventImportItemRequest item) {
        NormalizedLocalEvent normalized = new LocalEventNormalizer(LocalEventTestData.geoEverywhere(), objectMapper)
                .normalize(item, LocalEventNormalizer.Mode.IMPORT, NOW);
        return LocalEvent.builder()
                .id(UUID.randomUUID())
                .source("ticketbox.vn")
                .externalId("evt-123")
                .publishStatus("PUBLISHED")
                .contentHash(normalized.getContentHash())
                .manuallyEdited(false)
                .isDeleted(false)
                .dataVersion(4L)
                .build();
    }
}

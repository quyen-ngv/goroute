package com.ds.goroute.localevent;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.dto.LocalEventImportItemRequest;
import com.ds.goroute.localevent.dto.LocalEventPublishStatusRequest;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.localevent.service.LocalEventAdminService;
import com.ds.goroute.localevent.service.LocalEventAssembler;
import com.ds.goroute.localevent.service.LocalEventNormalizer;
import com.ds.goroute.localevent.service.LocalEventResponses;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.LocalEventPublishStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ds.goroute.localevent.LocalEventTestData.NOW;
import static com.ds.goroute.localevent.LocalEventTestData.concert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalEventAdminServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final LocalEventRepository repository = mock(LocalEventRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final UUID admin = UUID.randomUUID();
    private LocalEventAdminService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new LocalEventAdminService(repository,
                new LocalEventNormalizer(LocalEventTestData.geoEverywhere(), objectMapper),
                new LocalEventAssembler(repository, storage, objectMapper),
                new LocalEventResponses(objectMapper),
                mock(FileUploadService.class));
        Field clock = LocalEventAdminService.class.getDeclaredField("clock");
        clock.setAccessible(true);
        clock.set(service, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        when(repository.findImagesByEvent(any())).thenReturn(List.of());
    }

    @Test
    void aConsoleEventIsCreatedWithSourceAdminAndAnOwnStorageImageIsReadyAtOnce() {
        LocalEventImportItemRequest request = concert();
        request.getImages().get(0).setUrl("https://onestudy.id.vn/resource/goroute/events/uploads/a.webp");
        when(storage.extractObjectKey("https://onestudy.id.vn/resource/goroute/events/uploads/a.webp"))
                .thenReturn("events/uploads/a.webp");
        when(repository.findById(any())).thenAnswer(call -> Optional.of(
                LocalEvent.builder().id(call.getArgument(0)).publishStatus("DRAFT").build()));

        service.create(request, admin);

        ArgumentCaptor<LocalEvent> inserted = ArgumentCaptor.forClass(LocalEvent.class);
        verify(repository).insert(inserted.capture());
        assertThat(inserted.getValue().getSource()).isEqualTo("admin");
        assertThat(inserted.getValue().getExternalId()).isNull();
        assertThat(inserted.getValue().getCoverUrl())
                .isEqualTo("https://onestudy.id.vn/resource/goroute/events/uploads/a.webp");
    }

    @Test
    void anInvalidConsoleEventIsA400WithTheFieldErrors() {
        LocalEventImportItemRequest request = concert();
        request.setCategory("CIRCUS");

        assertThatThrownBy(() -> service.create(request, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("category: INVALID_ENUM")
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.LOCAL_EVENT_INVALID));
    }

    @Test
    void editingAnImportedEventProtectsItFromTheNextImport() {
        UUID eventId = UUID.randomUUID();
        when(repository.findById(eventId)).thenReturn(Optional.of(LocalEvent.builder().id(eventId)
                .source("ticketbox.vn").externalId("evt-123").publishStatus("PUBLISHED")
                .isDeleted(false).manuallyEdited(false).dataVersion(2L).build()));
        when(repository.updateContent(any(), anyLong())).thenReturn(true);
        LocalEventImportItemRequest request = concert();
        request.setExpectedVersion(2L);

        service.update(eventId, request, admin);

        ArgumentCaptor<LocalEvent> updated = ArgumentCaptor.forClass(LocalEvent.class);
        verify(repository).updateContent(updated.capture(), eq(2L));
        assertThat(updated.getValue().getManuallyEdited()).isTrue();
        assertThat(updated.getValue().getUpdatedBy()).isEqualTo(admin);
        assertThat(updated.getValue().getPublishStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void aStaleVersionIsAConflict() {
        UUID eventId = UUID.randomUUID();
        when(repository.findById(eventId)).thenReturn(Optional.of(LocalEvent.builder().id(eventId)
                .source("admin").isDeleted(false).dataVersion(5L).build()));
        LocalEventImportItemRequest request = concert();
        request.setExpectedVersion(4L);

        assertThatThrownBy(() -> service.update(eventId, request, admin))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.ALREADY_PROCESSED));
        verify(repository, never()).updateContent(any(), anyLong());
    }

    @Test
    void publishingStampsThePublicationTime() {
        UUID eventId = UUID.randomUUID();
        when(repository.findById(eventId)).thenReturn(Optional.of(LocalEvent.builder().id(eventId)
                .isDeleted(false).dataVersion(1L).build()));
        when(repository.updatePublishStatus(any(), anyLong(), any(), any(), any(), any())).thenReturn(true);

        service.changePublishStatus(eventId,
                new LocalEventPublishStatusRequest(LocalEventPublishStatus.PUBLISHED, 1L), admin);

        verify(repository).updatePublishStatus(eventId, 1L, "PUBLISHED", NOW, admin, NOW);
    }

    @Test
    void aDeletedEventReadsAsNotFound() {
        UUID eventId = UUID.randomUUID();
        when(repository.findById(eventId)).thenReturn(Optional.of(LocalEvent.builder().id(eventId)
                .isDeleted(true).build()));

        assertThatThrownBy(() -> service.detail(eventId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.LOCAL_EVENT_NOT_FOUND));
    }
}

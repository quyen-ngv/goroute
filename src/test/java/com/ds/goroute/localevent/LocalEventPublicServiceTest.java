package com.ds.goroute.localevent;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.persistence.LocalEventGeoSearchParams;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.localevent.service.LocalEventPublicService;
import com.ds.goroute.localevent.service.LocalEventResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ds.goroute.localevent.LocalEventTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalEventPublicServiceTest {

    private final LocalEventRepository repository = mock(LocalEventRepository.class);
    private final LocalEventPublicService service =
            new LocalEventPublicService(repository, new LocalEventResponses(new ObjectMapper().findAndRegisterModules()));

    @BeforeEach
    void setUp() throws Exception {
        Field clock = LocalEventPublicService.class.getDeclaredField("clock");
        clock.setAccessible(true);
        clock.set(service, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        when(repository.findPublicWithinRadius(any())).thenReturn(List.of());
    }

    @Test
    void theDefaultWindowIsTheNextSixtyDaysAroundTheRadiusBox() {
        service.search(21.0, 105.8, 30, null, null, "music, festival", 1, 20);

        ArgumentCaptor<LocalEventGeoSearchParams> params = ArgumentCaptor.forClass(LocalEventGeoSearchParams.class);
        verify(repository).findPublicWithinRadius(params.capture());
        assertThat(params.getValue().getFrom()).isEqualTo(NOW);
        assertThat(params.getValue().getTo()).isEqualTo(NOW.plusDays(60));
        assertThat(params.getValue().getCategories()).containsExactly("MUSIC", "FESTIVAL");
        assertThat(params.getValue().getMinLat()).isLessThan(21.0).isGreaterThan(20.7);
        assertThat(params.getValue().getOffset()).isEqualTo(20);
    }

    @Test
    void offsetWindowBoundsAreConvertedToUtc() {
        service.search(21.0, 105.8, 30, "2026-10-10T00:00:00+07:00", "2026-10-12T00:00:00Z", null, 0, 20);

        ArgumentCaptor<LocalEventGeoSearchParams> params = ArgumentCaptor.forClass(LocalEventGeoSearchParams.class);
        verify(repository).findPublicWithinRadius(params.capture());
        assertThat(params.getValue().getFrom()).isEqualTo(LocalDateTime.of(2026, 10, 9, 17, 0));
        assertThat(params.getValue().getTo()).isEqualTo(LocalDateTime.of(2026, 10, 12, 0, 0));
    }

    @Test
    void badWindowsAndCategoriesAre400() {
        assertInvalid(() -> service.search(21, 105, 30, "2026-10-10T00:00:00", null, null, 0, 20));
        assertInvalid(() -> service.search(21, 105, 30, "2026-10-10T00:00:00Z", "2026-10-09T00:00:00Z", null, 0, 20));
        assertInvalid(() -> service.search(21, 105, 30, "2026-10-10T00:00:00Z", "2027-10-12T00:00:00Z", null, 0, 20));
        assertInvalid(() -> service.search(21, 105, 30, null, null, "CIRCUS", 0, 20));
    }

    @Test
    void anUnpublishedEventIsNotFound() {
        UUID eventId = UUID.randomUUID();
        when(repository.findPublishedById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(eventId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.LOCAL_EVENT_NOT_FOUND));
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.INVALID_PARAMETERS));
    }
}

package com.ds.goroute.service.place;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreatePlaceEditSuggestionRequest;
import com.ds.goroute.dto.response.PlaceEditSuggestionResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceEditChange;
import com.ds.goroute.entity.PlaceEditGuard;
import com.ds.goroute.entity.PlaceEditSuggestion;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceEditSuggestionRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PlaceEditChangeStatus;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditStatus;
import com.ds.goroute.type.PlaceEditType;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceStatusReportReason;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The sending rules of plan §3.6. */
class PlaceEditSuggestionServiceTest {

    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final BetaAccessService beta = mock(BetaAccessService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceTranslationService translations = mock(PlaceTranslationService.class);
    private final PlaceEditSuggestionRepository suggestions = mock(PlaceEditSuggestionRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final PlaceEditSuggestionService service = new PlaceEditSuggestionService(
            new PlaceEditAccess(config, beta, users),
            new PlaceEditApplier(placeRepository, translations, config, mapper),
            new PlaceEditResponseAssembler(mapper),
            suggestions, placeRepository, translations, config, storage, mapper);

    private final UUID userId = UUID.randomUUID();
    private Place place;

    @BeforeEach
    void setUp() {
        when(config.getBoolean(BusinessConfigKey.PLACE_EDIT_ENABLED)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_MIN_ACCOUNT_AGE_HOURS)).thenReturn(24);
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_SUBMISSIONS_PER_DAY)).thenReturn(10);
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_PENDING_PER_USER)).thenReturn(30);
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_PIN_MOVE_METERS)).thenReturn(2000);
        when(config.getText(BusinessConfigKey.PLACE_EDIT_WEBSITE_BLOCKED_HOSTS)).thenReturn("bit.ly");
        when(users.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).createdAt(LocalDateTime.now().minusDays(30)).build()));
        place = Place.builder()
                .id(UUID.randomUUID())
                .title("Phở Thìn")
                .placeGroup(PlaceGroup.FOOD_AND_DRINK)
                .phone("024 1111")
                .status("")
                .latitude(new BigDecimal("21.0171"))
                .longitude(new BigDecimal("105.8552"))
                .visibilityStatus(PlaceVisibilityStatus.ACTIVE)
                .build();
        when(placeRepository.findById(place.getId())).thenReturn(Optional.of(place));
        guard(false);
    }

    @Test
    void theFlagOffRefusesEveryoneButBetaUsers() {
        when(config.getBoolean(BusinessConfigKey.PLACE_EDIT_ENABLED)).thenReturn(false);
        assertThatThrownBy(() -> service.submit(userId, place.getId(), phone("0909 123 456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.PLACE_EDIT_DISABLED));

        when(beta.isBetaUser(userId)).thenReturn(true);
        assertThat(service.submit(userId, place.getId(), phone("0909 123 456")).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void aPartnerPlaceTakesOnlyAClosureReport() {
        guard(true);
        assertThatThrownBy(() -> service.submit(userId, place.getId(), phone("0909 123 456")))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.PLACE_EDIT_NOT_ALLOWED));

        CreatePlaceEditSuggestionRequest report = CreatePlaceEditSuggestionRequest.builder()
                .type(PlaceEditType.STATUS_REPORT)
                .changes(CreatePlaceEditSuggestionRequest.Changes.builder()
                        .businessStatus(CreatePlaceEditSuggestionRequest.BusinessStatus.builder()
                                .reason(PlaceStatusReportReason.PERMANENTLY_CLOSED).build())
                        .build())
                .build();
        assertThat(service.submit(userId, place.getId(), report).getType()).isEqualTo("STATUS_REPORT");
    }

    @Test
    void aNewAccountMayNotSuggest() {
        when(users.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).createdAt(LocalDateTime.now().minusHours(2)).build()));

        assertThatThrownBy(() -> service.submit(userId, place.getId(), phone("0909 123 456")))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.PLACE_EDIT_NOT_ALLOWED));
    }

    @Test
    void theDailyCapIsEnforced() {
        when(suggestions.countSubmittedSince(eq(userId), any())).thenReturn(10L);

        assertThatThrownBy(() -> service.submit(userId, place.getId(), phone("0909 123 456")))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.PLACE_EDIT_RATE_LIMITED));
        verify(suggestions, never()).insertSuggestion(any());
    }

    @Test
    void aValueEqualToTheCurrentOneIsDroppedAndNothingLeftIsRefused() {
        assertThatThrownBy(() -> service.submit(userId, place.getId(), phone("024 1111")))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.PLACE_EDIT_NO_CHANGES));
    }

    @Test
    void aDescriptionAloneIsASuggestion() {
        CreatePlaceEditSuggestionRequest request = CreatePlaceEditSuggestionRequest.builder()
                .type(PlaceEditType.EDIT_INFO)
                .description("Quán mở 7h-22h, nghỉ thứ 2, có chỗ đậu ô tô")
                .build();

        PlaceEditSuggestionResponse response = service.submit(userId, place.getId(), request);

        ArgumentCaptor<PlaceEditSuggestion> saved = ArgumentCaptor.forClass(PlaceEditSuggestion.class);
        verify(suggestions).insertSuggestion(saved.capture());
        assertThat(saved.getValue().getDescription()).startsWith("Quán mở 7h-22h");
        assertThat(saved.getValue().getStatus()).isEqualTo(PlaceEditStatus.PENDING);
        assertThat(response.getChanges()).isEmpty();
        verify(suggestions, never()).insertChange(any());
    }

    @Test
    void myOwnPendingValueForTheSameFieldMovesToTheNewSuggestion() {
        UUID olderSuggestionId = UUID.randomUUID();
        PlaceEditChange older = PlaceEditChange.builder()
                .id(UUID.randomUUID())
                .suggestionId(olderSuggestionId)
                .field(PlaceEditField.PHONE)
                .status(PlaceEditChangeStatus.PENDING)
                .build();
        when(suggestions.findPendingUserChange(userId, place.getId(), PlaceEditField.PHONE))
                .thenReturn(Optional.of(older));
        when(suggestions.findSuggestion(olderSuggestionId)).thenReturn(Optional.of(PlaceEditSuggestion.builder()
                .id(olderSuggestionId).userId(userId).status(PlaceEditStatus.PENDING).build()));

        service.submit(userId, place.getId(), phone("0909 123 456"));

        verify(suggestions).moveChange(eq(older.getId()), any(UUID.class), anyString(),
                eq("{\"phone\":\"0909 123 456\"}"), any());
        verify(suggestions, never()).insertChange(any());
        // The older suggestion had nothing else in it, so it leaves the queue.
        verify(suggestions).cancel(eq(olderSuggestionId), eq(userId), any());
    }

    @Test
    void aStatusReportCarriesNothingButTheStatus() {
        CreatePlaceEditSuggestionRequest request = CreatePlaceEditSuggestionRequest.builder()
                .type(PlaceEditType.STATUS_REPORT)
                .changes(CreatePlaceEditSuggestionRequest.Changes.builder().phone("0909 123 456").build())
                .build();

        assertThatThrownBy(() -> service.submit(userId, place.getId(), request))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.INVALID_PARAMETERS));
    }

    @Test
    void onlyTheSubmitterCancelsAndOnlyWhilePending() {
        UUID suggestionId = UUID.randomUUID();
        when(suggestions.findSuggestion(suggestionId)).thenReturn(Optional.of(PlaceEditSuggestion.builder()
                .id(suggestionId).userId(UUID.randomUUID()).status(PlaceEditStatus.PENDING).build()));
        assertThatThrownBy(() -> service.cancel(userId, suggestionId))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.PLACE_EDIT_NOT_FOUND));

        when(suggestions.findSuggestion(suggestionId)).thenReturn(Optional.of(PlaceEditSuggestion.builder()
                .id(suggestionId).userId(userId).status(PlaceEditStatus.APPROVED).build()));
        assertThatThrownBy(() -> service.cancel(userId, suggestionId))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.PLACE_EDIT_ALREADY_REVIEWED));
    }

    private void guard(boolean partner) {
        when(placeRepository.findEditGuard(place.getId())).thenReturn(Optional.of(PlaceEditGuard.builder()
                .id(place.getId())
                .visibilityStatus("ACTIVE")
                .lifecycleStatus("ACTIVE")
                .status("")
                .partnerPlace(partner)
                .build()));
    }

    private static CreatePlaceEditSuggestionRequest phone(String phone) {
        return CreatePlaceEditSuggestionRequest.builder()
                .type(PlaceEditType.EDIT_INFO)
                .changes(CreatePlaceEditSuggestionRequest.Changes.builder().phone(phone).build())
                .build();
    }
}

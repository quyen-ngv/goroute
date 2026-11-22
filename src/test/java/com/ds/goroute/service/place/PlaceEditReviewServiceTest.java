package com.ds.goroute.service.place;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.ReviewPlaceEditSuggestionRequest;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceEditChange;
import com.ds.goroute.entity.PlaceEditGuard;
import com.ds.goroute.entity.PlaceEditSubmitterStats;
import com.ds.goroute.entity.PlaceEditSuggestion;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceContributorRepository;
import com.ds.goroute.repository.PlaceEditSuggestionRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PlaceEditAuthor;
import com.ds.goroute.type.PlaceEditChangeStatus;
import com.ds.goroute.type.PlaceEditDecisionAction;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditRejectReason;
import com.ds.goroute.type.PlaceEditReviewChannel;
import com.ds.goroute.type.PlaceEditStatus;
import com.ds.goroute.type.PlaceEditType;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The review of plan §3.8. */
class PlaceEditReviewServiceTest {

    private final PlaceEditSuggestionRepository suggestions = mock(PlaceEditSuggestionRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceContributorRepository contributors = mock(PlaceContributorRepository.class);
    private final PlaceTranslationService translations = mock(PlaceTranslationService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final PlaceEditNotifier notifier = mock(PlaceEditNotifier.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final PlaceEditReviewService service = new PlaceEditReviewService(suggestions, placeRepository,
            contributors, translations, users, new PlaceEditApplier(placeRepository, translations, config, mapper),
            new PlaceEditResponseAssembler(mapper), notifier, mapper);

    private final UUID reviewerId = UUID.randomUUID();
    private final UUID submitterId = UUID.randomUUID();
    private Place place;
    private PlaceEditSuggestion suggestion;
    private final List<PlaceEditChange> changes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_PIN_MOVE_METERS)).thenReturn(2000);
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
        suggestion = PlaceEditSuggestion.builder()
                .id(UUID.randomUUID())
                .placeId(place.getId())
                .userId(submitterId)
                .type(PlaceEditType.EDIT_INFO)
                .status(PlaceEditStatus.PENDING)
                .description("Quán mở 7h-22h, có chỗ đậu ô tô")
                .locale("vi")
                .build();
        when(suggestions.lockSuggestion(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(suggestions.findSuggestion(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(suggestions.findChangesBySuggestionIds(List.of(suggestion.getId())))
                .thenAnswer(invocation -> Map.of(suggestion.getId(), changes));
        when(suggestions.recordReview(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(suggestions.findSubmitterStats(any())).thenReturn(new PlaceEditSubmitterStats(0, 0));
        when(placeRepository.findEditGuard(place.getId())).thenReturn(Optional.of(PlaceEditGuard.builder()
                .id(place.getId()).visibilityStatus("ACTIVE").lifecycleStatus("ACTIVE").build()));
        when(placeRepository.findById(place.getId())).thenReturn(Optional.of(place));
    }

    @Test
    void approvingOneChangeAndRejectingAnotherIsAPartialApproval() {
        PlaceEditChange phone = change(PlaceEditField.PHONE, "{\"phone\":\"0909 123 456\"}");
        PlaceEditChange pin = change(PlaceEditField.LOCATION, "{\"latitude\":21.018,\"longitude\":105.856}");

        service.review(suggestion.getId(), request(List.of(
                decision(phone, PlaceEditDecisionAction.APPROVE),
                decision(pin, PlaceEditDecisionAction.REJECT)), List.of(), null),
                reviewerId, PlaceEditReviewChannel.CONSOLE);

        verify(placeRepository).updatePhone(place.getId(), "0909 123 456");
        verify(placeRepository, never()).updateLocation(any(), any(), any());
        verify(suggestions).decideChange(eq(phone.getId()), eq(PlaceEditChangeStatus.APPROVED), any(), eq(null), any());
        verify(suggestions).decideChange(eq(pin.getId()), eq(PlaceEditChangeStatus.REJECTED), eq(null),
                eq(PlaceEditRejectReason.INCORRECT), any());
        verify(suggestions).recordReview(eq(suggestion.getId()), eq(PlaceEditStatus.PARTIALLY_APPROVED), eq(null),
                any(), eq(PlaceEditReviewChannel.CONSOLE), eq(reviewerId), any());
        verify(contributors).credit(place.getId(), submitterId);
        verify(notifier).afterReview(eq(suggestion.getId()), eq(place.getId()), eq(submitterId), eq(true),
                eq(null), any(), any(), eq(reviewerId));
    }

    @Test
    void anOperatorTurnsADescriptionIntoChangesCreditedToTheSubmitter() {
        ReviewPlaceEditSuggestionRequest.AdminChange attributes = ReviewPlaceEditSuggestionRequest.AdminChange.builder()
                .field(PlaceEditField.ATTRIBUTES)
                .value(json("{\"attributes\":{\"parking_available\":{\"value\":true}}}"))
                .build();

        service.review(suggestion.getId(), request(List.of(), List.of(attributes), null),
                reviewerId, PlaceEditReviewChannel.CONSOLE);

        ArgumentCaptor<PlaceEditChange> inserted = ArgumentCaptor.forClass(PlaceEditChange.class);
        verify(suggestions).insertChange(inserted.capture());
        assertThat(inserted.getValue().getAuthor()).isEqualTo(PlaceEditAuthor.ADMIN);
        assertThat(inserted.getValue().getStatus()).isEqualTo(PlaceEditChangeStatus.APPROVED);
        assertThat(inserted.getValue().getUserId()).isEqualTo(submitterId);
        verify(placeRepository).updateAttributes(eq(place.getId()), any());
        verify(suggestions).recordReview(eq(suggestion.getId()), eq(PlaceEditStatus.APPROVED), eq(null),
                any(), any(), any(), any());
        verify(contributors).credit(place.getId(), submitterId);
    }

    @Test
    void anApprovedFieldSupersedesOtherPeoplesPendingValues() {
        PlaceEditChange phone = change(PlaceEditField.PHONE, "{\"phone\":\"0909 123 456\"}");

        service.review(suggestion.getId(), request(List.of(decision(phone, PlaceEditDecisionAction.APPROVE)),
                List.of(), null), reviewerId, PlaceEditReviewChannel.CONSOLE);

        verify(suggestions).supersedePending(eq(place.getId()), eq(PlaceEditField.PHONE),
                eq(suggestion.getId()), any());
    }

    @Test
    void aSuggestionIsReviewedOnce() {
        suggestion.setStatus(PlaceEditStatus.APPROVED);

        assertThatThrownBy(() -> service.review(suggestion.getId(), request(List.of(), List.of(),
                PlaceEditRejectReason.SPAM), reviewerId, PlaceEditReviewChannel.CONSOLE))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.PLACE_EDIT_ALREADY_REVIEWED));
        verify(suggestions, never()).recordReview(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void nothingAppliedNeedsAReasonAndCreditsNobody() {
        assertThatThrownBy(() -> service.review(suggestion.getId(), request(List.of(), List.of(), null),
                reviewerId, PlaceEditReviewChannel.CONSOLE))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.INVALID_PARAMETERS));

        service.review(suggestion.getId(), request(List.of(), List.of(), PlaceEditRejectReason.NOT_VERIFIABLE),
                reviewerId, PlaceEditReviewChannel.CONSOLE);

        verify(suggestions).recordReview(eq(suggestion.getId()), eq(PlaceEditStatus.REJECTED),
                eq(PlaceEditRejectReason.NOT_VERIFIABLE), any(), any(), any(), any());
        verify(contributors, never()).credit(any(), any());
        verify(notifier).afterReview(any(), any(), any(), eq(false), eq("NOT_VERIFIABLE"), any(), any(), any());
    }

    @Test
    void everyPendingChangeNeedsADecision() {
        change(PlaceEditField.PHONE, "{\"phone\":\"0909 123 456\"}");

        assertThatThrownBy(() -> service.review(suggestion.getId(), request(List.of(), List.of(),
                PlaceEditRejectReason.OTHER), reviewerId, PlaceEditReviewChannel.CONSOLE))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.INVALID_PARAMETERS));
        verify(notifier, never()).afterReview(any(), any(), any(), anyBoolean(), any(), any(), any(), any());
    }

    private PlaceEditChange change(PlaceEditField field, String newValue) {
        PlaceEditChange change = PlaceEditChange.builder()
                .id(UUID.randomUUID())
                .suggestionId(suggestion.getId())
                .placeId(place.getId())
                .userId(submitterId)
                .field(field)
                .newValue(newValue)
                .author(PlaceEditAuthor.USER)
                .status(PlaceEditChangeStatus.PENDING)
                .build();
        changes.add(change);
        return change;
    }

    private static ReviewPlaceEditSuggestionRequest.Decision decision(PlaceEditChange change,
                                                                     PlaceEditDecisionAction action) {
        return ReviewPlaceEditSuggestionRequest.Decision.builder()
                .changeId(change.getId())
                .action(action)
                .rejectReason(action == PlaceEditDecisionAction.REJECT ? PlaceEditRejectReason.INCORRECT : null)
                .build();
    }

    private static ReviewPlaceEditSuggestionRequest request(List<ReviewPlaceEditSuggestionRequest.Decision> decisions,
                                                            List<ReviewPlaceEditSuggestionRequest.AdminChange> admin,
                                                            PlaceEditRejectReason rejectReason) {
        return ReviewPlaceEditSuggestionRequest.builder()
                .decisions(decisions)
                .adminChanges(admin)
                .rejectReason(rejectReason)
                .build();
    }

    private com.fasterxml.jackson.databind.JsonNode json(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

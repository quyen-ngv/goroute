package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.request.AssistantReplyRequest;
import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.request.UpdateActivityRequest;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.dto.response.AssistantProposalResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.AssistantProposal;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.MarketplaceConversationAccessService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.WebSocketService;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantProposalServiceTest {

    private static final UUID TRIP = UUID.randomUUID();
    private static final UUID OTHER_TRIP = UUID.randomUUID();
    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID ASKER = UUID.randomUUID();
    private static final UUID EDITOR = UUID.randomUUID();
    private static final UUID PLACE = UUID.randomUUID();
    private static final UUID ACTIVITY = UUID.randomUUID();
    private static final UUID FOREIGN_ACTIVITY = UUID.randomUUID();

    @Mock private AssistantRepository repository;
    @Mock private TripRepository trips;
    @Mock private ActivityRepository activities;
    @Mock private PlaceRepository places;
    @Mock private ActivityService activityService;
    @Mock private AssistantTripDays tripDays;
    @Mock private TripAccessGuard tripAccessGuard;
    @Mock private MarketplaceConversationAccessService conversationAccess;
    @Mock private WebSocketService webSocketService;

    private AssistantProposalService service;
    private final AssistantTurn turn = AssistantTurn.builder().id(UUID.randomUUID()).conversationId(CONVERSATION)
            .tripId(TRIP).userId(ASKER).build();

    @BeforeEach
    void setUp() {
        service = new AssistantProposalService(repository, trips, activities, places, activityService, tripDays, tripAccessGuard,
                conversationAccess, webSocketService, new ObjectMapper().registerModule(new JavaTimeModule()));
        Trip trip = Trip.builder().id(TRIP).startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2026, 11, 3)).build();
        when(trips.findById(TRIP)).thenReturn(Optional.of(trip));
        when(places.findById(PLACE)).thenReturn(Optional.of(Place.builder().id(PLACE).title("Bún chả cá")
                .placeGroup(PlaceGroup.FOOD_AND_DRINK).visibilityStatus(PlaceVisibilityStatus.ACTIVE).build()));
        when(activities.findById(ACTIVITY)).thenReturn(Optional.of(Activity.builder().id(ACTIVITY).tripId(TRIP)
                .dayNumber(1).name("Cầu Rồng").build()));
        when(activities.findById(FOREIGN_ACTIVITY)).thenReturn(Optional.of(Activity.builder().id(FOREIGN_ACTIVITY)
                .tripId(OTHER_TRIP).dayNumber(1).name("Elsewhere").build()));
    }

    private static AssistantReplyRequest.ProposalDraft draft(String action) {
        AssistantReplyRequest.ProposalDraft draft = new AssistantReplyRequest.ProposalDraft();
        draft.setAction(action);
        return draft;
    }

    @Test
    void onlyProposalsNamingThisTripsDaysAndActivitiesAreKept() {
        AssistantReplyRequest.ProposalDraft add = draft("ADD");
        add.setPlaceId(PLACE.toString());
        add.setDayNumber(2);
        add.setStartTime("19:00");
        add.setEndTime("18:00");          // before the start: dropped, the add stays
        AssistantReplyRequest.ProposalDraft outsideTrip = draft("ADD");
        outsideTrip.setPlaceId(PLACE.toString());
        outsideTrip.setDayNumber(9);
        AssistantReplyRequest.ProposalDraft foreign = draft("REMOVE");
        foreign.setActivityId(FOREIGN_ACTIVITY.toString());
        AssistantReplyRequest.ProposalDraft move = draft("MOVE");
        move.setActivityId(ACTIVITY.toString());
        move.setToDayNumber(3);

        List<UUID> created = service.createFromReply(turn, TRIP, List.of(add, outsideTrip, foreign, move));

        assertThat(created).hasSize(2);
        ArgumentCaptor<AssistantProposal> stored = ArgumentCaptor.forClass(AssistantProposal.class);
        verify(repository, org.mockito.Mockito.times(2)).insertProposal(stored.capture());
        assertThat(stored.getAllValues().get(0).getPayload())
                .contains("\"placeName\":\"Bún chả cá\"").contains("\"startTime\":\"19:00\"")
                .contains("\"category\":\"restaurant\"").doesNotContain("endTime");
        assertThat(stored.getAllValues().get(1).getAction()).isEqualTo("MOVE");
    }

    @Test
    void applyingAnAddCreatesTheActivityAsThePersonWhoTapped() {
        UUID proposalId = pending("ADD", "{\"placeId\":\"" + PLACE + "\",\"dayNumber\":2,\"startTime\":\"19:00\",\"category\":\"restaurant\"}");
        when(activityService.createActivity(eq(TRIP), any(), eq(EDITOR)))
                .thenReturn(ActivityResponse.builder().id(UUID.randomUUID()).build());

        service.apply(EDITOR, proposalId);

        verify(tripAccessGuard).requireEditAccess(TRIP, EDITOR);
        ArgumentCaptor<CreateActivityRequest> request = ArgumentCaptor.forClass(CreateActivityRequest.class);
        verify(activityService).createActivity(eq(TRIP), request.capture(), eq(EDITOR));
        assertThat(request.getValue().getDayNumber()).isEqualTo(2);
        assertThat(request.getValue().getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(request.getValue().getPlaceId()).isEqualTo(PLACE.toString());
        verify(repository).resolveProposal(eq(proposalId), eq("APPLIED"), eq(EDITOR), any(), any(), eq(null));
    }

    @Test
    void anActivityDeletedMeanwhileMakesTheProposalStale() {
        UUID gone = UUID.randomUUID();
        UUID proposalId = pending("REMOVE", "{\"activityId\":\"" + gone + "\"}");

        service.apply(EDITOR, proposalId);

        verify(activityService, never()).deleteActivity(any(), any(), any());
        verify(repository).resolveProposal(eq(proposalId), eq("STALE"), eq(EDITOR), any(), eq(null), any());
    }

    @Test
    void aViewerCannotApply() {
        UUID proposalId = pending("REMOVE", "{\"activityId\":\"" + ACTIVITY + "\"}");
        doThrow(new BusinessException(4030001, "viewer")).when(tripAccessGuard).requireEditAccess(TRIP, ASKER);

        assertThatThrownBy(() -> service.apply(ASKER, proposalId)).isInstanceOf(BusinessException.class);
        verify(activityService, never()).deleteActivity(any(), any(), any());
    }

    @Test
    void anAlreadyResolvedProposalIsReturnedAsItIs() {
        UUID proposalId = UUID.randomUUID();
        AssistantProposal applied = AssistantProposal.builder().id(proposalId).conversationId(CONVERSATION).tripId(TRIP)
                .action("ADD").status("APPLIED").payload("{}").build();
        when(repository.lockProposal(proposalId)).thenReturn(Optional.of(applied));
        when(repository.findProposal(proposalId)).thenReturn(Optional.of(applied));

        AssistantProposalResponse response = service.apply(EDITOR, proposalId);

        assertThat(response.getStatus()).isEqualTo("APPLIED");
        verify(activityService, never()).createActivity(any(), any(), any());
    }

    @Test
    void anExtensionLetsTheSameAnswerAddToTheNewDayAndIsStoredFirst() {
        AssistantReplyRequest.ProposalDraft add = draft("ADD");
        add.setPlaceId(PLACE.toString());
        add.setDayNumber(4);
        AssistantReplyRequest.ProposalDraft extend = draft("ADD_DAYS");
        extend.setToDayNumber(4);
        extend.setStartTime("08:00");      // an extension has no time

        List<UUID> created = service.createFromReply(turn, TRIP, List.of(add, extend));

        assertThat(created).hasSize(2);
        ArgumentCaptor<AssistantProposal> stored = ArgumentCaptor.forClass(AssistantProposal.class);
        verify(repository, org.mockito.Mockito.times(2)).insertProposal(stored.capture());
        assertThat(stored.getAllValues().get(0).getAction()).isEqualTo("ADD_DAYS");
        assertThat(stored.getAllValues().get(0).getPayload())
                .contains("\"dayNumber\":4").contains("\"toDayNumber\":4").doesNotContain("startTime");
        assertThat(stored.getAllValues().get(1).getAction()).isEqualTo("ADD");
    }

    @Test
    void withoutAnExtensionADayAfterTheTripIsDropped() {
        AssistantReplyRequest.ProposalDraft add = draft("ADD");
        add.setPlaceId(PLACE.toString());
        add.setDayNumber(4);

        assertThat(service.createFromReply(turn, TRIP, List.of(add))).isEmpty();
    }

    @Test
    void anExtensionThatShortensOrAddsTooManyDaysIsDropped() {
        AssistantReplyRequest.ProposalDraft same = draft("ADD_DAYS");
        same.setToDayNumber(3);
        AssistantReplyRequest.ProposalDraft tooMany = draft("ADD_DAYS");
        tooMany.setToDayNumber(3 + AssistantProposalService.MAX_ADDED_DAYS + 1);

        assertThat(service.createFromReply(turn, TRIP, List.of(same, tooMany))).isEmpty();
    }

    @Test
    void applyingAnAddOnANewDayFirstLengthensTheTripAsThePersonWhoTapped() {
        UUID proposalId = pending("ADD", "{\"placeId\":\"" + PLACE + "\",\"dayNumber\":4}");
        when(activityService.createActivity(eq(TRIP), any(), eq(EDITOR)))
                .thenReturn(ActivityResponse.builder().id(UUID.randomUUID()).build());

        service.apply(EDITOR, proposalId);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(tripDays, activityService);
        order.verify(tripDays).extendTo(TRIP, 4, EDITOR);
        order.verify(activityService).createActivity(eq(TRIP), any(), eq(EDITOR));
    }

    @Test
    void applyingAnExtensionAsksTheTripDaysForItsLastDay() {
        UUID proposalId = pending("ADD_DAYS", "{\"dayNumber\":4,\"toDayNumber\":5,\"baseStartDate\":\"2026-11-01\"}");

        service.apply(EDITOR, proposalId);

        verify(tripDays).extendTo(TRIP, 5, EDITOR);
        verify(repository).resolveProposal(eq(proposalId), eq("APPLIED"), eq(EDITOR), any(), eq(null), eq(null));
    }

    @Test
    void aReplacementKeepsTheActivityAndPointsItAtTheNewPlace() {
        UUID other = UUID.randomUUID();
        when(places.findById(other)).thenReturn(Optional.of(Place.builder().id(other).title("Hải sản Bé Mặn")
                .placeGroup(PlaceGroup.FOOD_AND_DRINK).visibilityStatus(PlaceVisibilityStatus.ACTIVE).build()));
        AssistantReplyRequest.ProposalDraft replace = draft("REPLACE");
        replace.setActivityId(ACTIVITY.toString());
        replace.setPlaceId(other.toString());

        service.createFromReply(turn, TRIP, List.of(replace));
        ArgumentCaptor<AssistantProposal> stored = ArgumentCaptor.forClass(AssistantProposal.class);
        verify(repository).insertProposal(stored.capture());
        assertThat(stored.getValue().getPayload()).contains("\"activityName\":\"Cầu Rồng\"")
                .contains("\"placeName\":\"Hải sản Bé Mặn\"").contains("\"category\":\"restaurant\"");

        UUID proposalId = pending("REPLACE", stored.getValue().getPayload());
        service.apply(EDITOR, proposalId);

        verify(activities).assignPlace(eq(ACTIVITY), eq(other), any(), any());
        ArgumentCaptor<UpdateActivityRequest> update = ArgumentCaptor.forClass(UpdateActivityRequest.class);
        verify(activityService).updateActivity(eq(TRIP), eq(ACTIVITY), update.capture(), eq(EDITOR));
        assertThat(update.getValue().getName()).isEqualTo("Hải sản Bé Mặn");
        assertThat(update.getValue().getDayNumber()).isNull();
    }

    @Test
    void removingDaysSaysWhatTheTripBecomesAndHowManyActivitiesGo() {
        when(activities.findByTripId(TRIP)).thenReturn(List.of(
                Activity.builder().id(ACTIVITY).tripId(TRIP).dayNumber(3).build(),
                Activity.builder().id(UUID.randomUUID()).tripId(TRIP).dayNumber(1).build()));
        AssistantReplyRequest.ProposalDraft remove = draft("REMOVE_DAYS");
        remove.setDayNumber(3);

        service.createFromReply(turn, TRIP, List.of(remove));

        ArgumentCaptor<AssistantProposal> stored = ArgumentCaptor.forClass(AssistantProposal.class);
        verify(repository).insertProposal(stored.capture());
        assertThat(stored.getValue().getPayload()).contains("\"toDayNumber\":3").contains("\"activityCount\":1")
                .contains("\"newStartDate\":\"2026-11-01\"").contains("\"newEndDate\":\"2026-11-02\"")
                .contains("\"baseStartDate\":\"2026-11-01\"");
    }

    @Test
    void aDayReshapeGoesAloneInItsAnswer() {
        AssistantReplyRequest.ProposalDraft add = draft("ADD");
        add.setPlaceId(PLACE.toString());
        add.setDayNumber(2);
        AssistantReplyRequest.ProposalDraft swap = draft("SWAP_DAYS");
        swap.setDayNumber(3);
        swap.setToDayNumber(1);
        AssistantReplyRequest.ProposalDraft prepend = draft("PREPEND_DAYS");
        prepend.setDays(1);
        AssistantReplyRequest.ProposalDraft retime = draft("UPDATE_TIME");
        retime.setActivityId(ACTIVITY.toString());
        retime.setStartTime("09:00");

        List<UUID> created = service.createFromReply(turn, TRIP, List.of(add, swap, prepend, retime));

        assertThat(created).hasSize(2);
        ArgumentCaptor<AssistantProposal> stored = ArgumentCaptor.forClass(AssistantProposal.class);
        verify(repository, org.mockito.Mockito.times(2)).insertProposal(stored.capture());
        assertThat(stored.getAllValues()).extracting(AssistantProposal::getAction)
                .containsExactly("SWAP_DAYS", "UPDATE_TIME");
        assertThat(stored.getAllValues().get(0).getPayload()).contains("\"dayNumber\":1").contains("\"toDayNumber\":3");
    }

    @Test
    void impossibleDayChangesAreDropped() {
        AssistantReplyRequest.ProposalDraft removeAll = draft("REMOVE_DAYS");
        removeAll.setDayNumber(1);
        removeAll.setToDayNumber(3);
        AssistantReplyRequest.ProposalDraft sameDay = draft("SWAP_DAYS");
        sameDay.setDayNumber(2);
        sameDay.setToDayNumber(2);
        AssistantReplyRequest.ProposalDraft tooEarly = draft("PREPEND_DAYS");
        tooEarly.setDays(AssistantProposalService.MAX_ADDED_DAYS + 1);
        AssistantReplyRequest.ProposalDraft sameStart = draft("SHIFT_DATES");
        sameStart.setStartDate("2026-11-01");

        for (AssistantReplyRequest.ProposalDraft draft : List.of(removeAll, sameDay, tooEarly, sameStart)) {
            assertThat(service.createFromReply(turn, TRIP, List.of(draft))).as(draft.getAction()).isEmpty();
        }
    }

    @Test
    void movingTheTripKeepsItsLength() {
        AssistantReplyRequest.ProposalDraft shift = draft("SHIFT_DATES");
        shift.setStartDate("2026-11-08");

        service.createFromReply(turn, TRIP, List.of(shift));

        ArgumentCaptor<AssistantProposal> stored = ArgumentCaptor.forClass(AssistantProposal.class);
        verify(repository).insertProposal(stored.capture());
        assertThat(stored.getValue().getPayload()).contains("\"newStartDate\":\"2026-11-08\"")
                .contains("\"newEndDate\":\"2026-11-10\"");
        UUID proposalId = pending("SHIFT_DATES", stored.getValue().getPayload());

        service.apply(EDITOR, proposalId);

        verify(tripDays).shiftDates(TRIP, LocalDate.of(2026, 11, 8), EDITOR);
    }

    @Test
    void aDayChangeProposedBeforeTheTripWasRenumberedGoesStale() {
        UUID proposalId = pending("REMOVE_DAYS",
                "{\"dayNumber\":2,\"toDayNumber\":2,\"baseStartDate\":\"2026-10-31\"}");

        service.apply(EDITOR, proposalId);

        verify(tripDays, never()).removeDays(any(), anyInt(), anyInt(), any());
        verify(repository).resolveProposal(eq(proposalId), eq("STALE"), eq(EDITOR), any(), eq(null), any());
    }

    private UUID pending(String action, String payload) {
        UUID proposalId = UUID.randomUUID();
        AssistantProposal proposal = AssistantProposal.builder().id(proposalId).conversationId(CONVERSATION).tripId(TRIP)
                .action(action).status("PENDING").payload(payload).build();
        when(repository.lockProposal(proposalId)).thenReturn(Optional.of(proposal));
        when(repository.findProposal(proposalId)).thenReturn(Optional.of(proposal));
        return proposalId;
    }
}

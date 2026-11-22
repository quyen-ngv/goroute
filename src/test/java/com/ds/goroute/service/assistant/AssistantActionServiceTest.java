package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AssistantReplyRequest;
import com.ds.goroute.entity.AssistantTurn;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.meetup.service.MeetupService;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.TripAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantActionServiceTest {

    private static final UUID ASKER = UUID.randomUUID();

    @Mock private TripAccessGuard tripAccessGuard;
    @Mock private LocalEventRepository events;
    @Mock private MeetupService meetups;
    @Mock private LocationImageRepository areas;

    private AssistantActionService service;
    private final AssistantTurn turn = AssistantTurn.builder().id(UUID.randomUUID()).userId(ASKER).build();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

    @BeforeEach
    void setUp() {
        service = new AssistantActionService(tripAccessGuard, events, meetups, areas);
    }

    @Test
    void createTripNamesTheCatalogueCityAndKeepsFutureDates() {
        UUID hue = UUID.randomUUID();
        LocationImage area = LocationImage.builder().id(hue).fullAddress("Huế").build();
        when(areas.findBestMatch(anyString())).thenReturn(Optional.of(area));
        when(areas.findById(hue)).thenReturn(Optional.of(area));

        List<Map<String, Object>> blocks = service.blocks(turn, List.of(
                draft("CREATE_TRIP", "Tạo chuyến Huế", d -> {
                    d.setCity("hue");
                    d.setStartDate(today.plusDays(4).toString());
                    d.setEndDate(today.plusDays(6).toString());
                })), text -> text);

        assertThat(blocks).singleElement().satisfies(block -> assertThat(block)
                .containsEntry("kind", "ACTION").containsEntry("action", "CREATE_TRIP")
                .containsEntry("cityId", hue.toString()).containsEntry("destination", "Huế")
                .containsEntry("startDate", today.plusDays(4).toString())
                .containsEntry("label", "Tạo chuyến Huế"));
    }

    @Test
    void destinationCardsCarryTheAreaForTheCardAndOnlyUsableDates() {
        UUID sapa = UUID.randomUUID();
        when(areas.findById(any())).thenAnswer(call -> sapa.equals(call.getArgument(0))
                ? Optional.of(LocationImage.builder().id(sapa).fullAddress("Sa Pa").slogan("Thị trấn trong sương")
                        .imageUrl("https://img/sapa.jpg").build())
                : Optional.empty());
        AssistantReplyRequest.DestinationRef good = destination(sapa, "Mùa lúa chín", today.plusDays(10), today.plusDays(12));
        AssistantReplyRequest.DestinationRef again = destination(sapa, "x", null, null);
        AssistantReplyRequest.DestinationRef unknown = destination(UUID.randomUUID(), "bịa", null, null);
        AssistantReplyRequest.DestinationRef backwards = destination(sapa, "y", today.plusDays(5), today.plusDays(2));

        List<Map<String, Object>> blocks = service.destinationBlocks(List.of(good, again, unknown), text -> text);
        List<Map<String, Object>> undated = service.destinationBlocks(List.of(backwards), text -> text);

        assertThat(blocks).singleElement().satisfies(block -> assertThat(block)
                .containsEntry("kind", "DESTINATION").containsEntry("cityId", sapa.toString())
                .containsEntry("name", "Sa Pa").containsEntry("slogan", "Thị trấn trong sương")
                .containsEntry("imageUrl", "https://img/sapa.jpg").containsEntry("note", "Mùa lúa chín")
                .containsEntry("startDate", today.plusDays(10).toString()));
        assertThat(undated.get(0)).doesNotContainKeys("startDate", "endDate");
    }

    private static AssistantReplyRequest.DestinationRef destination(UUID id, String note, LocalDate start, LocalDate end) {
        AssistantReplyRequest.DestinationRef ref = new AssistantReplyRequest.DestinationRef();
        ref.setId(id.toString());
        ref.setNote(note);
        ref.setStartDate(start == null ? null : start.toString());
        ref.setEndDate(end == null ? null : end.toString());
        return ref;
    }

    @Test
    void pastDatesAreLeftOutButTheButtonStays() {
        when(areas.findBestMatch(anyString())).thenReturn(Optional.empty());

        List<Map<String, Object>> blocks = service.blocks(turn, List.of(
                draft("CREATE_TRIP", "Tạo chuyến", d -> {
                    d.setCity("Côn Đảo");
                    d.setStartDate(today.minusDays(3).toString());
                    d.setEndDate(today.minusDays(1).toString());
                })), text -> text);

        assertThat(blocks).singleElement().satisfies(block -> assertThat(block)
                .containsEntry("destination", "Côn Đảo").doesNotContainKey("startDate").doesNotContainKey("cityId"));
    }

    @Test
    void aTripTheAskerCannotReadIsDropped() {
        UUID tripId = UUID.randomUUID();
        when(tripAccessGuard.canRead(tripId, ASKER)).thenReturn(false);

        assertThat(service.blocks(turn, List.of(draft("OPEN_TRIP", "Mở chuyến", d -> d.setTripId(tripId.toString()))),
                text -> text)).isEmpty();
    }

    @Test
    void anInvisibleMeetupAndABlockedLabelAreDropped() {
        UUID meetupId = UUID.randomUUID();
        when(meetups.detail(any(), any())).thenThrow(new BusinessException(ErrorConstant.NOT_FOUND, "x"));

        assertThat(service.blocks(turn, List.of(
                draft("OPEN_MEETUP", "Xem", d -> d.setMeetupId(meetupId.toString())),
                draft("CREATE_TRIP", "blocked", d -> { })), text -> "blocked".equals(text) ? null : text)).isEmpty();
    }

    @Test
    void atMostThreeButtons() {
        List<AssistantReplyRequest.ActionDraft> drafts = List.of(
                draft("CREATE_TRIP", "A", d -> d.setCity("a")), draft("CREATE_TRIP", "B", d -> d.setCity("b")),
                draft("CREATE_TRIP", "C", d -> d.setCity("c")), draft("CREATE_TRIP", "D", d -> d.setCity("d")));
        when(areas.findBestMatch(anyString())).thenReturn(Optional.empty());

        assertThat(service.blocks(turn, drafts, text -> text)).hasSize(3);
    }

    private static AssistantReplyRequest.ActionDraft draft(String action, String label,
                                                           java.util.function.Consumer<AssistantReplyRequest.ActionDraft> fill) {
        AssistantReplyRequest.ActionDraft draft = new AssistantReplyRequest.ActionDraft();
        draft.setAction(action);
        draft.setLabel(label);
        fill.accept(draft);
        return draft;
    }
}

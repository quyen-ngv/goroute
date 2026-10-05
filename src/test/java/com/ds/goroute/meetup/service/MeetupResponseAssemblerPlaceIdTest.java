package com.ds.goroute.meetup.service;

import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.dto.MeetupDetailResponse;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.type.MeetupStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The detail carries the catalogue {@code placeId} under the same rule as {@code address}: the
 * app's edit form sends it back, and the update writes {@code place_id} from the request, so a
 * detail without it made every host edit drop the link.
 */
@DisplayName("Meetup detail placeId follows the location reveal rule")
class MeetupResponseAssemblerPlaceIdTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 3, 0);

    private final MeetupRepository repository = mock(MeetupRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MeetupResponseAssembler assembler = new MeetupResponseAssembler(repository, users);

    private final UUID hostId = UUID.randomUUID();
    private final UUID placeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(repository.countJoined(any())).thenReturn(Map.of());
        when(users.findByIds(any())).thenReturn(List.of());
    }

    private Meetup exactMeetup(MeetupJoinPolicy policy) {
        return Meetup.builder()
                .id(UUID.randomUUID())
                .hostUserId(hostId)
                .title("đi cà phê muối")
                .locationMode(MeetupLocationMode.EXACT)
                .latitude(new BigDecimal("21.0285"))
                .longitude(new BigDecimal("105.8542"))
                .displayLatitude(new BigDecimal("21.0290"))
                .displayLongitude(new BigDecimal("105.8550"))
                .placeId(placeId)
                .placeName("Cà phê Giảng")
                .address("39 Nguyễn Hữu Huân")
                .joinPolicy(policy)
                .status(MeetupStatus.APPROVED)
                .visibleUntil(NOW.plusHours(5))
                .resubmitCount(0)
                .build();
    }

    @Test
    @DisplayName("the host gets placeId back for the edit form")
    void hostSeesPlaceId() {
        MeetupDetailResponse detail = assembler.detail(exactMeetup(MeetupJoinPolicy.APPROVAL), hostId, null, NOW, 2);

        assertThat(detail.isLocationHidden()).isFalse();
        assertThat(detail.getPlaceId()).isEqualTo(placeId);
    }

    @Test
    @DisplayName("a viewer the spot is hidden from gets no placeId")
    void hiddenForOutsiders() {
        MeetupParticipant requested = MeetupParticipant.builder().status(MeetupParticipantStatus.REQUESTED).build();

        MeetupDetailResponse detail = assembler.detail(exactMeetup(MeetupJoinPolicy.APPROVAL), UUID.randomUUID(),
                requested, NOW, 2);

        assertThat(detail.isLocationHidden()).isTrue();
        assertThat(detail.getPlaceId()).isNull();
        assertThat(detail.getAddress()).isNull();
    }
}

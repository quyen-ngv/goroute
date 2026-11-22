package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.request.SenderLocationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantAskerLocationsTest {

    private static final AssistantAskerLocation HERE =
            new AssistantAskerLocation(new BigDecimal("11.8000"), new BigDecimal("108.3000"));

    private static SenderLocationRequest request(String latitude, String longitude, String accuracy) {
        SenderLocationRequest request = new SenderLocationRequest();
        request.setLatitude(latitude == null ? null : new BigDecimal(latitude));
        request.setLongitude(longitude == null ? null : new BigDecimal(longitude));
        request.setAccuracyMeters(accuracy == null ? null : new BigDecimal(accuracy));
        return request;
    }

    @Test
    void thePositionIsRoundedToAboutElevenMetresBeforeItIsKept() {
        AssistantAskerLocation location = AssistantAskerLocation.of(request("11.80004999", "108.30005001", "12.5"));

        assertThat(location.lat()).isEqualTo(new BigDecimal("11.8000"));
        assertThat(location.lng()).isEqualTo(new BigDecimal("108.3001"));
    }

    @Test
    void nothingSentOrHalfAPositionIsNoPosition() {
        assertThat(AssistantAskerLocation.of(null)).isNull();
        assertThat(AssistantAskerLocation.of(request("11.8", null, null))).isNull();
        assertThat(AssistantAskerLocation.of(request(null, "108.3", null))).isNull();
    }

    @Test
    void aFixTooCoarseForNearMeIsNoPosition() {
        assertThat(AssistantAskerLocation.of(request("11.8", "108.3", "3000"))).isNotNull();
        assertThat(AssistantAskerLocation.of(request("11.8", "108.3", "3000.5"))).isNull();
        assertThat(AssistantAskerLocation.of(request("11.8", "108.3", null))).isNotNull();
    }

    @Test
    void theCoordinatesNeverPrint() {
        assertThat(HERE.toString()).doesNotContain("11.8").doesNotContain("108.3");
        assertThat(request("11.8", "108.3", "5").toString()).doesNotContain("11.8").doesNotContain("108.3");
    }

    @Test
    void aPositionIsFoundOnlyForItsOwnTurnUntilItIsForgotten() {
        AssistantAskerLocations locations = new AssistantAskerLocations();
        UUID turn = UUID.randomUUID();

        locations.remember(turn, HERE);

        assertThat(locations.find(turn)).contains(HERE);
        assertThat(locations.find(UUID.randomUUID())).isEmpty();
        locations.forget(turn);
        assertThat(locations.find(turn)).isEmpty();
        assertThat(locations.size()).isZero();
    }

    @Test
    void noPositionAndNoTurnAreIgnored() {
        AssistantAskerLocations locations = new AssistantAskerLocations();

        locations.remember(UUID.randomUUID(), null);
        locations.remember(null, HERE);
        locations.forget(null);

        assertThat(locations.size()).isZero();
        assertThat(locations.find(null)).isEmpty();
    }

    @Test
    void aPositionExpiresAndTheNextOneSweepsTheOldOnesOut() {
        AtomicLong now = new AtomicLong(1_000);
        AssistantAskerLocations locations = new AssistantAskerLocations();
        locations.useClock(now::get);
        UUID old = UUID.randomUUID();
        UUID unread = UUID.randomUUID();
        locations.remember(old, HERE);
        locations.remember(unread, HERE);

        now.addAndGet(AssistantAskerLocations.TTL.toMillis() - 1);
        assertThat(locations.find(old)).contains(HERE);

        now.addAndGet(1);
        assertThat(locations.find(old)).isEmpty();
        assertThat(locations.size()).isEqualTo(1);

        locations.remember(UUID.randomUUID(), HERE);
        assertThat(locations.size()).isEqualTo(1);
    }

    @Test
    void aFullStoreKeepsNoNewPosition() {
        AssistantAskerLocations locations = new AssistantAskerLocations();
        locations.useClock(() -> 0L);
        for (int i = 0; i < AssistantAskerLocations.MAX_ENTRIES; i++) locations.remember(UUID.randomUUID(), HERE);
        UUID late = UUID.randomUUID();

        locations.remember(late, HERE);

        assertThat(locations.size()).isEqualTo(AssistantAskerLocations.MAX_ENTRIES);
        assertThat(locations.find(late)).isEmpty();
    }
}

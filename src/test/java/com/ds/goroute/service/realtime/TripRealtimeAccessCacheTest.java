package com.ds.goroute.service.realtime;

import com.ds.goroute.service.TripAccessGuard;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripRealtimeAccessCacheTest {

    private final TripAccessGuard guard = mock(TripAccessGuard.class);
    private final TripRealtimeAccessCache cache = new TripRealtimeAccessCache(guard);

    @Test
    void asksTheDatabaseOncePerSubscriberWhileTheAnswerIsFresh() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(guard.canRead(tripId, userId)).thenReturn(true);

        assertThat(cache.canRead(tripId, userId)).isTrue();
        assertThat(cache.canRead(tripId, userId)).isTrue();

        verify(guard, times(1)).canRead(tripId, userId);
    }

    @Test
    void evictingATripMakesEverySubscriberOfItReReadMembership() {
        UUID tripId = UUID.randomUUID();
        UUID otherTripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(guard.canRead(tripId, userId)).thenReturn(true, false);
        when(guard.canRead(otherTripId, userId)).thenReturn(true);
        cache.canRead(tripId, userId);
        cache.canRead(otherTripId, userId);

        cache.evictTrip(tripId);

        assertThat(cache.canRead(tripId, userId)).isFalse();
        assertThat(cache.canRead(otherTripId, userId)).isTrue();
        verify(guard, times(1)).canRead(otherTripId, userId);
    }

    @Test
    void aFailedLookupIsNotRememberedSoTheNextOneTriesAgain() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(guard.canRead(tripId, userId))
                .thenThrow(new IllegalStateException("database down"))
                .thenReturn(true);

        assertThatThrownBy(() -> cache.canRead(tripId, userId)).isInstanceOf(IllegalStateException.class);
        assertThat(cache.canRead(tripId, userId)).isTrue();
    }
}

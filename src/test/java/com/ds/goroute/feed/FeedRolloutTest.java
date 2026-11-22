package com.ds.goroute.feed;

import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedRolloutTest {

    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final BetaAccessService beta = mock(BetaAccessService.class);
    private final FeedRollout rollout = new FeedRollout(config, beta);

    @Test
    void offForEveryoneButBetaUsersByDefault() {
        UUID betaUser = UUID.randomUUID();
        when(beta.isBetaUser(betaUser)).thenReturn(true);

        assertThat(rollout.enabledFor(betaUser)).isTrue();
        assertThat(rollout.enabledFor(UUID.randomUUID())).isFalse();
        assertThat(rollout.enabledFor(null)).isFalse();
    }

    @Test
    void onForEveryoneIncludingGuestsOnceEnabled() {
        when(config.getBoolean(BusinessConfigKey.FEED_RANKING_ENABLED)).thenReturn(true);

        assertThat(rollout.enabledFor(UUID.randomUUID())).isTrue();
        assertThat(rollout.enabledFor(null)).isTrue();
    }

    @Test
    void aPercentageRollsOutToRoughlyThatShareAndIsStablePerUser() {
        when(config.getInt(BusinessConfigKey.FEED_RANKING_PERCENT)).thenReturn(30);

        long on = IntStream.range(0, 4000).filter(i -> rollout.enabledFor(UUID.randomUUID())).count();
        UUID someone = UUID.randomUUID();
        boolean first = rollout.enabledFor(someone);

        assertThat(on).isBetween(1000L, 1400L);
        assertThat(IntStream.range(0, 20).allMatch(i -> rollout.enabledFor(someone) == first)).isTrue();
    }

    @Test
    void theBucketIsAlwaysBetweenZeroAndNinetyNine() {
        assertThat(IntStream.range(0, 2000).map(i -> FeedRollout.bucket(UUID.randomUUID())))
                .allMatch(bucket -> bucket >= 0 && bucket < 100);
    }
}

package com.ds.goroute.feed;

import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedWeightsProviderTest {

    @Test
    void shipsTheDefaultsWhenNothingIsOverridden() {
        FeedWeights w = FeedTestSupport.defaults();

        assertThat(w.d("engagement.save", 0)).isEqualTo(6.0);
        assertThat(w.i("caps.sessionSize", 0)).isEqualTo(500);
        assertThat(w.d("freshness.halfLifeHoursTrip", 0)).isEqualTo(168);
        assertThat(w.i("tripGate.minStops", 0)).isEqualTo(3);
    }

    @Test
    void anOverrideReplacesOnlyTheKeysItNamesDeepInTheTree() {
        FeedWeights w = FeedTestSupport.weights("{\"engagement\":{\"save\":9},\"rerank\":{\"authorMaxPer10\":3}}");

        assertThat(w.d("engagement.save", 0)).isEqualTo(9.0);
        assertThat(w.d("engagement.comment", 0)).as("sibling kept").isEqualTo(4.0);
        assertThat(w.i("rerank.authorMaxPer10", 0)).isEqualTo(3);
        assertThat(w.d("rerank.authorDecay", 0)).as("sibling kept").isEqualTo(0.5);
    }

    @Test
    void invalidJsonKeepsTheDefaults() {
        FeedWeights w = FeedTestSupport.weights("{not json");

        assertThat(w.d("engagement.save", 0)).isEqualTo(6.0);
    }

    @Test
    void aMissingPathFallsBackToTheCallersDefault() {
        FeedWeights w = FeedTestSupport.defaults();

        assertThat(w.d("no.such.key", 1.5)).isEqualTo(1.5);
        assertThat(w.i("engagement", 7)).as("an object is not a number").isEqualTo(7);
        assertThat(w.b("caps.sessionSize", true)).as("a number is not a boolean").isTrue();
    }

    @Test
    void anUnreadableConfigKeepsServingWeights() {
        BusinessConfigService config = mock(BusinessConfigService.class);
        when(config.getText(BusinessConfigKey.FEED_RANKING_WEIGHTS)).thenThrow(new IllegalStateException("db down"));

        FeedWeights w = new FeedWeightsProvider(config, new ObjectMapper()).get();

        assertThat(w.d("engagement.like", 0)).isEqualTo(1.0);
    }
}

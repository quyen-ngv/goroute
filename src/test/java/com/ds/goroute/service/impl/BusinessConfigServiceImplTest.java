package com.ds.goroute.service.impl;

import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessConfigServiceImplTest {

    private final BusinessConfigStore store = mock(BusinessConfigStore.class);
    private final BusinessConfigServiceImpl service = new BusinessConfigServiceImpl(store);

    @Test
    void returnsValidatedDatabaseValue() {
        stub(BusinessConfigKey.PLACE_REVIEW_REFRESH_MAX_REVIEWS, "75");

        assertThat(service.getInt(BusinessConfigKey.PLACE_REVIEW_REFRESH_MAX_REVIEWS)).isEqualTo(75);
    }

    @Test
    void fallsBackToSafeDefaultForMissingOrInvalidValue() {
        stub(BusinessConfigKey.PLACE_REVIEW_REFRESH_MAX_REVIEWS, "999999");

        assertThat(service.getInt(BusinessConfigKey.PLACE_REVIEW_REFRESH_MAX_REVIEWS)).isEqualTo(200);
    }

    @Test
    void readsFreeTripMemoryLimitFromConfig() {
        stub(BusinessConfigKey.FREE_TRIP_MEMORY_LIMIT, "75");

        assertThat(service.getInt(BusinessConfigKey.FREE_TRIP_MEMORY_LIMIT)).isEqualTo(75);
    }

    @Test
    void fallsBackToFiftyForInvalidFreeTripMemoryLimit() {
        stub(BusinessConfigKey.FREE_TRIP_MEMORY_LIMIT, "1001");

        assertThat(service.getInt(BusinessConfigKey.FREE_TRIP_MEMORY_LIMIT)).isEqualTo(50);
    }

    @Test
    void readsBooleanFlags() {
        stub(BusinessConfigKey.CHECKIN_GALLERY_ALLOWED_FOR_FREE, "false");

        assertThat(service.getBoolean(BusinessConfigKey.CHECKIN_GALLERY_ALLOWED_FOR_FREE)).isFalse();
    }

    @Test
    void keepsCodeDefaultWhenBooleanValueIsNonsense() {
        stub(BusinessConfigKey.CHECKIN_GALLERY_ALLOWED_FOR_FREE, "maybe");

        assertThat(service.getBoolean(BusinessConfigKey.CHECKIN_GALLERY_ALLOWED_FOR_FREE)).isTrue();
    }

    @Test
    void readsDecimalsWithinRange() {
        stub(BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_SEXUAL, "0.6");

        assertThat(service.getDecimal(BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_SEXUAL)).isEqualTo(0.6d);
    }

    @Test
    void rejectsOutOfRangeDecimalAndKeepsDefault() {
        stub(BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_SEXUAL, "4.2");

        assertThat(service.getDecimal(BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_SEXUAL)).isEqualTo(0.85d);
    }

    @Test
    void readsEnumValuesAndFallsBackOnUnknownOnes() {
        stub(BusinessConfigKey.MODERATION_STRICTNESS_GROUP, "not-a-level");

        assertThat(service.getEnum(BusinessConfigKey.MODERATION_STRICTNESS_GROUP,
                com.ds.goroute.type.ModerationStrictness.class))
                .isEqualTo(com.ds.goroute.type.ModerationStrictness.KEYWORD_ONLY);
    }

    private void stub(BusinessConfigKey key, String value) {
        when(store.rawValue(any())).thenReturn(Optional.empty());
        when(store.rawValue(key)).thenReturn(Optional.of(value));
    }
}

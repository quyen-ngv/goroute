package com.ds.goroute.service.impl;

import com.ds.goroute.entity.AppConfig;
import com.ds.goroute.repository.AppConfigRepository;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessConfigServiceImplTest {

    private final AppConfigRepository repository = mock(AppConfigRepository.class);
    private final BusinessConfigServiceImpl service = new BusinessConfigServiceImpl(repository);

    @Test
    void returnsValidatedDatabaseValue() {
        when(repository.findActiveByLabelAndKey("PLACE_REVIEW", "DEFAULT_REFRESH_MAX_REVIEWS"))
                .thenReturn(Optional.of(AppConfig.builder().value("75").build()));

        assertThat(service.getInt(BusinessConfigKey.PLACE_REVIEW_REFRESH_MAX_REVIEWS)).isEqualTo(75);
    }

    @Test
    void fallsBackToSafeDefaultForMissingOrInvalidValue() {
        when(repository.findActiveByLabelAndKey("PLACE_REVIEW", "DEFAULT_REFRESH_MAX_REVIEWS"))
                .thenReturn(Optional.of(AppConfig.builder().value("999999").build()));

        assertThat(service.getInt(BusinessConfigKey.PLACE_REVIEW_REFRESH_MAX_REVIEWS)).isEqualTo(200);
    }

    @Test
    void readsFreeTripMemoryLimitFromConfig() {
        when(repository.findActiveByLabelAndKey("TRIP_MEMORY", "FREE_TRIP_MEMORY_LIMIT"))
                .thenReturn(Optional.of(AppConfig.builder().value("75").build()));

        assertThat(service.getInt(BusinessConfigKey.FREE_TRIP_MEMORY_LIMIT)).isEqualTo(75);
    }

    @Test
    void fallsBackToFiftyForInvalidFreeTripMemoryLimit() {
        when(repository.findActiveByLabelAndKey("TRIP_MEMORY", "FREE_TRIP_MEMORY_LIMIT"))
                .thenReturn(Optional.of(AppConfig.builder().value("1001").build()));

        assertThat(service.getInt(BusinessConfigKey.FREE_TRIP_MEMORY_LIMIT)).isEqualTo(50);
    }
}

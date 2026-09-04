package com.ds.goroute.service;

import com.ds.goroute.entity.UserQuotaOverride;
import com.ds.goroute.repository.UserQuotaOverrideRepository;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserQuotaPolicyServiceTest {
    private final UserQuotaOverrideRepository repository = mock(UserQuotaOverrideRepository.class);
    private final BusinessConfigService businessConfig = mock(BusinessConfigService.class);
    private final UserQuotaPolicyService service = new UserQuotaPolicyService(repository, businessConfig);
    private final UUID userId = UUID.randomUUID();

    @Test
    void anOverrideWinsOverTheGlobalFreeTripQuota() {
        when(repository.findByUserId(userId)).thenReturn(Optional.of(UserQuotaOverride.builder()
                .freeTripQuota(7).build()));

        assertThat(service.freeTripQuota(userId)).isEqualTo(7);
    }

    @Test
    void aNullOverrideInheritsTheGlobalAiQuota() {
        when(repository.findByUserId(userId)).thenReturn(Optional.of(UserQuotaOverride.builder()
                .aiTripFreeQuota(null).build()));
        when(businessConfig.getInt(BusinessConfigKey.AI_TRIP_FREE_QUOTA)).thenReturn(3);

        assertThat(service.aiTripQuota(userId, "FREE")).isEqualTo(3);
    }

    @Test
    void aUserCanHaveDifferentAiLimitsByTier() {
        when(repository.findByUserId(userId)).thenReturn(Optional.of(UserQuotaOverride.builder()
                .aiTripFreeQuota(2).aiTripProQuota(20).build()));

        assertThat(service.aiTripQuota(userId, "FREE")).isEqualTo(2);
        assertThat(service.aiTripQuota(userId, "PRO")).isEqualTo(20);
    }
}

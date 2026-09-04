package com.ds.goroute.service;

import com.ds.goroute.dto.request.UpdateUserQuotaOverridesRequest;
import com.ds.goroute.dto.response.AdminUserQuotaResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserQuotaOverride;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserQuotaOverrideRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserQuotaServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserQuotaOverrideRepository overrideRepository = mock(UserQuotaOverrideRepository.class);
    private final UserQuotaPolicyService policy = mock(UserQuotaPolicyService.class);
    private final BusinessConfigService businessConfig = mock(BusinessConfigService.class);
    private final AdminUserQuotaService service =
            new AdminUserQuotaService(userRepository, overrideRepository, policy, businessConfig);
    private final UUID userId = UUID.randomUUID();

    @Test
    void getShowsEffectiveGlobalAndPerUserValues() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(policy.findOverride(userId)).thenReturn(Optional.of(UserQuotaOverride.builder()
                .userId(userId).dataVersion(4L).freeTripQuota(7).build()));
        stubGlobalQuotas();

        AdminUserQuotaResponse response = service.get(userId);

        assertThat(response.getDataVersion()).isEqualTo(4L);
        assertThat(response.getFreeTrip().getGlobalLimit()).isEqualTo(3);
        assertThat(response.getFreeTrip().getOverrideLimit()).isEqualTo(7);
        assertThat(response.getFreeTrip().getEffectiveLimit()).isEqualTo(7);
        assertThat(response.getAiTripFree().getEffectiveLimit()).isEqualTo(3);
    }

    @Test
    void updateCreatesAnOverrideForTheSelectedUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(overrideRepository.findByUserId(userId))
                .thenReturn(Optional.empty());
        UserQuotaOverride saved = UserQuotaOverride.builder()
                .userId(userId).dataVersion(1L).aiTripProQuota(20).build();
        when(policy.findOverride(userId)).thenReturn(Optional.of(saved));
        when(overrideRepository.insert(any(UserQuotaOverride.class))).thenReturn(1);
        stubGlobalQuotas();

        UpdateUserQuotaOverridesRequest request = new UpdateUserQuotaOverridesRequest();
        request.setAiTripProQuota(20);
        AdminUserQuotaResponse response = service.update(userId, request);

        verify(overrideRepository).insert(any(UserQuotaOverride.class));
        verify(policy).evict(userId);
        assertThat(response.getAiTripPro().getOverrideLimit()).isEqualTo(20);
        assertThat(response.getAiTripPro().getEffectiveLimit()).isEqualTo(20);
    }

    @Test
    void updateRejectsAStaleVersionWhenNoRowMatches() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(overrideRepository.findByUserId(userId)).thenReturn(Optional.of(UserQuotaOverride.builder()
                .userId(userId).dataVersion(3L).build()));
        when(overrideRepository.update(any(UserQuotaOverride.class))).thenReturn(0);

        UpdateUserQuotaOverridesRequest request = new UpdateUserQuotaOverridesRequest();
        request.setExpectedVersion(2L);

        assertThatThrownBy(() -> service.update(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("changed by another administrator");
        verify(overrideRepository).update(any(UserQuotaOverride.class));
    }

    private void stubGlobalQuotas() {
        when(businessConfig.getInt(BusinessConfigKey.TRIP_FREE_CREATION_QUOTA)).thenReturn(3);
        when(businessConfig.getInt(BusinessConfigKey.AI_TRIP_FREE_QUOTA)).thenReturn(3);
        when(businessConfig.getInt(BusinessConfigKey.AI_TRIP_PRO_QUOTA)).thenReturn(10);
        when(businessConfig.getInt(BusinessConfigKey.SOCIAL_LOCATION_DAILY_JOB_LIMIT)).thenReturn(5);
    }
}

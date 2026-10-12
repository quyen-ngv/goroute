package com.ds.goroute.service.assistant;

import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantAccessServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock private BusinessConfigService businessConfig;
    @Mock private BetaAccessService betaAccess;
    @Mock private AiTripRepository subscriptions;
    @Mock private AssistantRepository repository;

    private AssistantAccessService service;

    @BeforeEach
    void setUp() {
        service = new AssistantAccessService(businessConfig, betaAccess, subscriptions, repository);
        when(businessConfig.getInt(BusinessConfigKey.ASSISTANT_FREE_DAILY_QUESTIONS)).thenReturn(5);
        when(businessConfig.getInt(BusinessConfigKey.ASSISTANT_PRO_DAILY_QUESTIONS)).thenReturn(50);
    }

    @Test
    void betaUsersMayAskWhileTheFeatureIsOff() {
        when(businessConfig.getBoolean(BusinessConfigKey.ASSISTANT_ENABLED)).thenReturn(false);
        when(betaAccess.isBetaUser(USER)).thenReturn(true);

        assertThat(service.isAvailable(USER)).isTrue();
        assertThat(service.isAvailable(AssistantIdentity.BOT_USER_ID)).isFalse();
        assertThat(service.isAvailable(null)).isFalse();
    }

    @Test
    void proGetsTheProAllowanceAndAnythingElseIsFree() {
        when(subscriptions.getSubscriptionTier(USER)).thenReturn("pro");
        assertThat(service.dailyLimit(USER)).isEqualTo(50);
        when(subscriptions.getSubscriptionTier(USER)).thenReturn(null);
        assertThat(service.dailyLimit(USER)).isEqualTo(5);
    }

    @Test
    void theDayStartsAtMidnightInVietnamWhateverTheServerZone() {
        // 2026-10-12 01:30 in Vietnam is 2026-10-11 18:30 UTC.
        service.clock = Clock.fixed(Instant.parse("2026-10-11T18:30:00Z"), ZoneId.of("UTC"));

        assertThat(service.startOfToday()).isEqualTo(LocalDateTime.of(2026, 10, 11, 17, 0));
    }
}

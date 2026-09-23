package com.ds.goroute.service.checkin;

import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.CheckinPhotoSource;
import com.ds.goroute.type.CheckinVerificationScope;
import com.ds.goroute.type.CheckinVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CheckinRewardCalculator with ward-level verification")
class CheckinRewardCalculatorWardTest {

    @Mock private BusinessConfigService config;
    private CheckinRewardCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CheckinRewardCalculator(config);
        when(config.getBoolean(BusinessConfigKey.CHECKIN_REWARD_ENABLED)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.CHECKIN_REWARD_BASE_POINTS)).thenReturn(10);
        when(config.getInt(BusinessConfigKey.CHECKIN_REWARD_DAILY_CAP)).thenReturn(50);
        when(config.getDecimal(BusinessConfigKey.CHECKIN_REWARD_CAMERA_MULTIPLIER)).thenReturn(1.0);
        when(config.getDecimal(BusinessConfigKey.CHECKIN_REWARD_GALLERY_MULTIPLIER)).thenReturn(0.4);
        when(config.getDecimal(BusinessConfigKey.CHECKIN_REWARD_UNVERIFIED_MULTIPLIER)).thenReturn(0.5);
        when(config.getDecimal(BusinessConfigKey.CHECKIN_REWARD_WARD_VERIFIED_MULTIPLIER)).thenReturn(0.7);
    }

    @Test
    @DisplayName("WARD scope pays between the place and the unverified rate, and says why")
    void wardScopeIsDiscounted() {
        CheckinRewardCalculator.Reward place = calculator.calculate(verified(CheckinVerificationScope.PLACE), 0);
        CheckinRewardCalculator.Reward ward = calculator.calculate(verified(CheckinVerificationScope.WARD), 0);

        assertThat(place.points()).isEqualTo(10);
        assertThat(ward.points()).isEqualTo(7);
        assertThat(ward.reasonCodes()).contains(CheckinRewardCalculator.REASON_LOCATION_WARD_ONLY)
                .doesNotContain(CheckinRewardCalculator.REASON_LOCATION_UNVERIFIED);
        assertThat(place.reasonCodes()).doesNotContain(CheckinRewardCalculator.REASON_LOCATION_WARD_ONLY);
    }

    private static UserCheckin verified(CheckinVerificationScope scope) {
        return UserCheckin.builder()
                .photoSource(CheckinPhotoSource.CAMERA)
                .verificationStatus(CheckinVerificationStatus.VERIFIED)
                .verificationScope(scope)
                .build();
    }
}

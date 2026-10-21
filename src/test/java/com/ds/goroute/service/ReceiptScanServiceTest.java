package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.ReceiptScanResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AiApiCallMapper;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.service.time.UserTimeZones;
import com.ds.goroute.thirdparty.ai.VisionAiClient;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiptScanServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Mock private VisionAiClient visionClient;
    @Mock private BusinessConfigService businessConfig;
    @Mock private BetaAccessService betaAccess;
    @Mock private AiTripRepository subscriptions;
    @Mock private AiApiCallMapper aiApiCallMapper;
    @Mock private UserTimeZones userTimeZones;

    private ReceiptScanService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptScanService(visionClient, businessConfig, betaAccess, subscriptions,
                aiApiCallMapper, new ObjectMapper(), userTimeZones);
        when(userTimeZones.zoneFor(org.mockito.ArgumentMatchers.any())).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        service.clock = Clock.fixed(Instant.parse("2026-10-13T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        when(visionClient.isConfigured()).thenReturn(true);
        when(businessConfig.getInt(BusinessConfigKey.EXPENSE_RECEIPT_SCAN_FREE_DAILY)).thenReturn(10);
        when(businessConfig.getInt(BusinessConfigKey.EXPENSE_RECEIPT_SCAN_PRO_DAILY)).thenReturn(50);
    }

    @Test
    void betaUsersMayScanWhileTheFeatureIsOffButOnlyWithAKey() {
        when(businessConfig.getBoolean(BusinessConfigKey.EXPENSE_RECEIPT_SCAN_ENABLED)).thenReturn(false);
        when(betaAccess.isBetaUser(USER)).thenReturn(true);
        assertThat(service.isAvailable(USER)).isTrue();

        when(visionClient.isConfigured()).thenReturn(false);
        assertThat(service.isAvailable(USER)).isFalse();
    }

    @Test
    void proGetsTheProAllowance() {
        when(subscriptions.getSubscriptionTier(USER)).thenReturn("pro");
        assertThat(service.dailyLimit(USER)).isEqualTo(50);
        when(subscriptions.getSubscriptionTier(USER)).thenReturn("FREE");
        assertThat(service.dailyLimit(USER)).isEqualTo(10);
    }

    @Test
    void aSpentAllowanceRejectsBeforeCallingTheModel() {
        when(businessConfig.getBoolean(BusinessConfigKey.EXPENSE_RECEIPT_SCAN_ENABLED)).thenReturn(true);
        when(aiApiCallMapper.countSucceededSince(eq(USER), eq("RECEIPT_SCAN"), any())).thenReturn(10L);

        assertThatThrownBy(() -> service.scan(USER, new MockMultipartFile("file", JPEG), "VND"))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code")
                .isEqualTo(ErrorConstant.EXPENSE_RECEIPT_SCAN_DAILY_LIMIT);
        verify(visionClient, never()).completeJson(anyString(), anyString(), any(), anyString());
    }

    @Test
    void anUploadThatIsNotAnImageIsRejected() {
        when(businessConfig.getBoolean(BusinessConfigKey.EXPENSE_RECEIPT_SCAN_ENABLED)).thenReturn(true);

        assertThatThrownBy(() -> service.scan(USER, new MockMultipartFile("file", "%PDF-1.4".getBytes()), null))
                .isInstanceOf(BusinessException.class)
                .extracting("error.code")
                .isEqualTo(ErrorConstant.EXPENSE_RECEIPT_SCAN_INVALID_IMAGE);
    }

    @Test
    void aProviderFailureIsUnreadableNotAnError() {
        when(businessConfig.getBoolean(BusinessConfigKey.EXPENSE_RECEIPT_SCAN_ENABLED)).thenReturn(true);
        when(visionClient.completeJson(anyString(), anyString(), any(), eq("image/jpeg"))).thenReturn(Optional.empty());

        ReceiptScanResponse result = service.scan(USER, new MockMultipartFile("file", JPEG), "VND");

        assertThat(result.isReadable()).isFalse();
        assertThat(result.getAmount()).isNull();
        assertThat(result.getDailyLimit()).isEqualTo(10);
    }

    @Test
    void parsesAVietnameseBillAndDropsTheTotalFromTheCandidates() {
        ReceiptScanResponse result = service.parse("""
                {"isReceipt":true,"total":450000.4,"currency":"vnd","merchant":"  Phở Thìn Lò Đúc ",
                 "date":"2026-10-12","category":"food",
                 "candidates":[{"label":"Tạm tính","amount":420000},{"label":"Tổng","amount":450000},
                               {"label":"x","amount":-5}]}""", "USD");

        assertThat(result.isReadable()).isTrue();
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("450000"));
        assertThat(result.getCurrency()).isEqualTo("VND");
        assertThat(result.getMerchant()).isEqualTo("Phở Thìn Lò Đúc");
        assertThat(result.getSpentOn()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(result.getCategory()).isEqualTo("FOOD");
        assertThat(result.getCandidates()).extracting(ReceiptScanResponse.Candidate::getLabel).containsExactly("Tạm tính");
    }

    @Test
    void fallsBackToTheHintCurrencyAndDiscardsImplausibleValues() {
        ReceiptScanResponse result = service.parse("""
                {"isReceipt":true,"total":"12.345","currency":"XX","date":"2031-01-01","category":"TAXI"}""", "USD");

        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("12.35"));
        assertThat(result.getSpentOn()).isNull();
        assertThat(result.getCategory()).isNull();
    }

    @Test
    void notAReceiptOrBrokenJsonIsUnreadable() {
        assertThat(service.parse("{\"isReceipt\":false,\"total\":10}", "VND").isReadable()).isFalse();
        assertThat(service.parse("not json", "VND").isReadable()).isFalse();
        assertThat(service.parse("{\"isReceipt\":true,\"total\":0}", "VND").isReadable()).isFalse();
    }
}

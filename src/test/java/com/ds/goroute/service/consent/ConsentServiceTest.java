package com.ds.goroute.service.consent;

import com.ds.goroute.dto.request.ConsentUpdateRequest;
import com.ds.goroute.dto.response.ConsentStateResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserConsent;
import com.ds.goroute.enums.ConsentPurpose;
import com.ds.goroute.enums.ConsentSource;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.repository.UserConsentRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsentServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock private UserConsentRepository consents;
    @Mock private UserRepository users;
    @Mock private BusinessConfigService config;
    @Mock private AssistantRepository assistant;

    private ConsentService service;

    @BeforeEach
    void setUp() {
        service = new ConsentService(consents, users, config, assistant);
        when(config.getText(BusinessConfigKey.LEGAL_TERMS_VERSION)).thenReturn("2.0");
        when(config.getText(BusinessConfigKey.LEGAL_PRIVACY_VERSION)).thenReturn("2.0");
        when(consents.findLatestByUser(USER)).thenReturn(List.of());
    }

    @Test
    void anAccountThatNeverAnsweredMustAcceptAndSeesItAsAnUpdateWhenItIsOld() {
        when(users.findById(USER)).thenReturn(Optional.of(user(LocalDateTime.now().minusDays(30))));

        ConsentStateResponse state = service.state(USER);

        assertThat(state.isNeedsAcceptance()).isTrue();
        assertThat(state.isExistingAccount()).isTrue();
        assertThat(state.getAnalytics()).isNull();
        assertThat(state.getAssistantMemory()).isNull();
    }

    @Test
    void anAccountCreatedMinutesAgoIsWelcomedRatherThanToldOfAnUpdate() {
        when(users.findById(USER)).thenReturn(Optional.of(user(LocalDateTime.now().minusMinutes(2))));

        assertThat(service.state(USER).isExistingAccount()).isFalse();
    }

    @Test
    void currentVersionsAndAgeMeanNothingIsAsked() {
        when(consents.findLatestByUser(USER)).thenReturn(List.of(
                row(ConsentPurpose.TERMS, true, "2.0"),
                row(ConsentPurpose.PRIVACY, true, "2.0"),
                row(ConsentPurpose.AGE_16, true, null),
                row(ConsentPurpose.ANALYTICS, false, null)));

        ConsentStateResponse state = service.state(USER);

        assertThat(state.isNeedsAcceptance()).isFalse();
        assertThat(state.getAnalytics()).isFalse();
    }

    @Test
    void anOlderTermsVersionAsksAgain() {
        when(consents.findLatestByUser(USER)).thenReturn(List.of(
                row(ConsentPurpose.TERMS, true, "1.0"),
                row(ConsentPurpose.PRIVACY, true, "2.0"),
                row(ConsentPurpose.AGE_16, true, null)));

        ConsentStateResponse state = service.state(USER);

        assertThat(state.isNeedsAcceptance()).isTrue();
        assertThat(state.isExistingAccount()).isTrue();
    }

    @Test
    void acceptingRecordsEachAnswerWithTheVersionShown() {
        service.record(USER, request(ConsentSource.CONSENT_SCREEN,
                item(ConsentPurpose.TERMS, true, "2.0"),
                item(ConsentPurpose.PRIVACY, true, "2.0"),
                item(ConsentPurpose.AGE_16, true, null),
                item(ConsentPurpose.ANALYTICS, false, null)));

        ArgumentCaptor<UserConsent> saved = ArgumentCaptor.forClass(UserConsent.class);
        verify(consents, times(4)).insert(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(UserConsent::getPurpose, UserConsent::getGranted, UserConsent::getDocumentVersion,
                        UserConsent::getSource)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("TERMS", true, "2.0", "CONSENT_SCREEN"),
                        org.assertj.core.groups.Tuple.tuple("PRIVACY", true, "2.0", "CONSENT_SCREEN"),
                        org.assertj.core.groups.Tuple.tuple("AGE_16", true, null, "CONSENT_SCREEN"),
                        org.assertj.core.groups.Tuple.tuple("ANALYTICS", false, null, "CONSENT_SCREEN"));
    }

    @Test
    void aStaleDocumentVersionIsRefused() {
        assertThatThrownBy(() -> service.record(USER, request(ConsentSource.CONSENT_SCREEN,
                item(ConsentPurpose.TERMS, true, "1.0"))))
                .isInstanceOf(BusinessException.class);
        verify(consents, never()).insert(any());
    }

    @Test
    void aRequiredPurposeCannotBeDeclined() {
        assertThatThrownBy(() -> service.record(USER, request(ConsentSource.CONSENT_SCREEN,
                item(ConsentPurpose.AGE_16, false, null))))
                .isInstanceOf(BusinessException.class);
        verify(consents, never()).insert(any());
    }

    @Test
    void turningPengooMemoryOffForgetsWhatItKept() {
        service.record(USER, request(ConsentSource.SETTINGS, item(ConsentPurpose.ASSISTANT_MEMORY, false, null)));

        verify(assistant).deleteProfile(USER);
    }

    @Test
    void turningPengooMemoryOnKeepsTheNotes() {
        service.record(USER, request(ConsentSource.SETTINGS, item(ConsentPurpose.ASSISTANT_MEMORY, true, null)));

        verify(assistant, never()).deleteProfile(any());
    }

    @Test
    void aPurposeNeverAnsweredIsNotGranted() {
        when(consents.findLatestGranted(USER, "ASSISTANT_MEMORY")).thenReturn(Optional.empty());

        assertThat(service.isGranted(USER, ConsentPurpose.ASSISTANT_MEMORY)).isFalse();
        assertThat(service.isGranted(null, ConsentPurpose.ASSISTANT_MEMORY)).isFalse();
    }

    private static User user(LocalDateTime createdAt) {
        User user = new User();
        user.setId(USER);
        user.setCreatedAt(createdAt);
        return user;
    }

    private static UserConsent row(ConsentPurpose purpose, boolean granted, String version) {
        return UserConsent.builder().userId(USER).purpose(purpose.name()).granted(granted)
                .documentVersion(version).createdAt(LocalDateTime.now()).build();
    }

    private static ConsentUpdateRequest.Item item(ConsentPurpose purpose, boolean granted, String version) {
        ConsentUpdateRequest.Item item = new ConsentUpdateRequest.Item();
        item.setPurpose(purpose);
        item.setGranted(granted);
        item.setDocumentVersion(version);
        return item;
    }

    private static ConsentUpdateRequest request(ConsentSource source, ConsentUpdateRequest.Item... items) {
        ConsentUpdateRequest request = new ConsentUpdateRequest();
        request.setSource(source);
        request.setItems(List.of(items));
        return request;
    }
}

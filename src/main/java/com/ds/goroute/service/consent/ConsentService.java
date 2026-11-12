package com.ds.goroute.service.consent;

import com.ds.goroute.constant.ErrorConstant;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The consent record (V278): what each person agreed to and when. Nothing here blocks an API
 * call: old app versions cannot show the consent screen, so the server never refuses them, and
 * the app gates itself on {@link #state}. Optional purposes are read by the features they switch
 * ({@link #isGranted}) and are off until the person turns them on.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    /** An account this old when the consent screen first loads was there before it. */
    private static final long NEW_ACCOUNT_HOURS = 1;

    private final UserConsentRepository consents;
    private final UserRepository users;
    private final BusinessConfigService config;
    private final AssistantRepository assistantRepository;

    @Transactional(readOnly = true)
    public ConsentStateResponse state(UUID userId) {
        Map<ConsentPurpose, UserConsent> latest = latest(userId);
        String termsVersion = config.getText(BusinessConfigKey.LEGAL_TERMS_VERSION);
        String privacyVersion = config.getText(BusinessConfigKey.LEGAL_PRIVACY_VERSION);
        String acceptedTerms = acceptedVersion(latest.get(ConsentPurpose.TERMS));
        String acceptedPrivacy = acceptedVersion(latest.get(ConsentPurpose.PRIVACY));
        boolean ageConfirmed = isTrue(latest.get(ConsentPurpose.AGE_16));
        User user = users.findById(userId).orElse(null);
        boolean existing = acceptedTerms != null
                || (user != null && user.getCreatedAt() != null
                && user.getCreatedAt().isBefore(LocalDateTime.now().minusHours(NEW_ACCOUNT_HOURS)));
        return ConsentStateResponse.builder()
                .termsVersion(termsVersion)
                .privacyVersion(privacyVersion)
                .acceptedTermsVersion(acceptedTerms)
                .acceptedPrivacyVersion(acceptedPrivacy)
                .ageConfirmed(ageConfirmed)
                .needsAcceptance(!Objects.equals(termsVersion, acceptedTerms)
                        || !Objects.equals(privacyVersion, acceptedPrivacy)
                        || !ageConfirmed)
                .existingAccount(existing)
                .analytics(answer(latest.get(ConsentPurpose.ANALYTICS)))
                .assistantMemory(answer(latest.get(ConsentPurpose.ASSISTANT_MEMORY)))
                .profileSearchIndexing(answer(latest.get(ConsentPurpose.PROFILE_SEARCH_INDEXING)))
                .build();
    }

    /** Appends the answers to the history and applies what withdrawing a purpose means. */
    @Transactional
    public ConsentStateResponse record(UUID userId, ConsentUpdateRequest request) {
        String termsVersion = config.getText(BusinessConfigKey.LEGAL_TERMS_VERSION);
        String privacyVersion = config.getText(BusinessConfigKey.LEGAL_PRIVACY_VERSION);
        ConsentSource source = request.getSource() == null ? ConsentSource.CONSENT_SCREEN : request.getSource();
        LocalDateTime now = LocalDateTime.now();
        for (ConsentUpdateRequest.Item item : request.getItems()) {
            ConsentPurpose purpose = item.getPurpose();
            boolean granted = Boolean.TRUE.equals(item.getGranted());
            if (purpose.isRequired() && !granted) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, purpose + " can only be given");
            }
            String version = switch (purpose) {
                case TERMS -> requireCurrent(item.getDocumentVersion(), termsVersion, purpose);
                case PRIVACY -> requireCurrent(item.getDocumentVersion(), privacyVersion, purpose);
                default -> null;
            };
            consents.insert(UserConsent.builder()
                    .userId(userId)
                    .purpose(purpose.name())
                    .granted(granted)
                    .documentVersion(version)
                    .source(source.name())
                    .platform(request.getPlatform())
                    .appVersion(request.getAppVersion())
                    .createdAt(now)
                    .build());
            if (purpose == ConsentPurpose.ASSISTANT_MEMORY && !granted) {
                // Withdrawing means Pengoo forgets, not only that it stops learning.
                assistantRepository.deleteProfile(userId);
            }
        }
        return state(userId);
    }

    /** The person's current answer for an optional purpose; never asked reads as no. */
    @Transactional(readOnly = true)
    public boolean isGranted(UUID userId, ConsentPurpose purpose) {
        return userId != null && consents.findLatestGranted(userId, purpose.name()).orElse(false);
    }

    private Map<ConsentPurpose, UserConsent> latest(UUID userId) {
        Map<ConsentPurpose, UserConsent> byPurpose = new EnumMap<>(ConsentPurpose.class);
        for (UserConsent row : consents.findLatestByUser(userId)) {
            try {
                byPurpose.put(ConsentPurpose.valueOf(row.getPurpose()), row);
            } catch (IllegalArgumentException ignored) {
                // A purpose written by a newer build that this one does not know.
            }
        }
        return byPurpose;
    }

    private static String requireCurrent(String given, String current, ConsentPurpose purpose) {
        if (!Objects.equals(given, current)) {
            // The documents changed while the screen was open: the app reloads and asks again.
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    purpose + " version " + given + " is not the current " + current);
        }
        return current;
    }

    private static String acceptedVersion(UserConsent row) {
        return isTrue(row) ? row.getDocumentVersion() : null;
    }

    private static boolean isTrue(UserConsent row) {
        return row != null && Boolean.TRUE.equals(row.getGranted());
    }

    private static Boolean answer(UserConsent row) {
        return row == null ? null : row.getGranted();
    }
}

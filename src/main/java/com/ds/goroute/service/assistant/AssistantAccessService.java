package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.response.AssistantMeResponse;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Who may ask Pengoo, and how deep one answer may search. What a question costs is charged in AI
 * credits ({@link com.ds.goroute.service.AiCreditService}), not counted here.
 */
@Service
@RequiredArgsConstructor
public class AssistantAccessService {

    static final String TIER_PRO = "PRO";
    static final String TIER_FREE = "FREE";

    private final BusinessConfigService businessConfig;
    private final BetaAccessService betaAccess;
    private final AiTripRepository subscriptions;

    /** Replaced in tests; turn timestamps are written with the same clock. */
    Clock clock = Clock.systemDefaultZone();

    public boolean isAvailable(UUID userId) {
        if (userId == null || AssistantIdentity.isAssistant(userId)) return false;
        return businessConfig.getBoolean(BusinessConfigKey.ASSISTANT_ENABLED) || betaAccess.isBetaUser(userId);
    }

    public String tier(UUID userId) {
        String tier = subscriptions.getSubscriptionTier(userId);
        return tier != null && TIER_PRO.equals(tier.trim().toUpperCase(Locale.ROOT)) ? TIER_PRO : TIER_FREE;
    }

    /** Tool calls one question may use: a PRO account gets a deeper search than a FREE one. */
    public int maxToolCalls(UUID userId) {
        return TIER_PRO.equals(tier(userId))
                ? businessConfig.getInt(BusinessConfigKey.ASSISTANT_PRO_MAX_TOOL_CALLS)
                : businessConfig.getInt(BusinessConfigKey.ASSISTANT_FREE_MAX_TOOL_CALLS);
    }

    public AssistantMeResponse me(UUID userId) {
        return AssistantMeResponse.builder()
                .available(isAvailable(userId))
                .tier(tier(userId))
                .botUserId(AssistantIdentity.BOT_USER_ID)
                .name(AssistantIdentity.NAME)
                .username(AssistantIdentity.USERNAME)
                .build();
    }

    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}

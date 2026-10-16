package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.response.AssistantMeResponse;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.AssistantRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.time.UserTimeZones;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Who may ask Pengoo and how much. A question counts against the asker's allowance for the
 * asker's own calendar day (their device zone, see {@link UserTimeZones}); the ledger is {@code assistant_turns} itself (rows with
 * {@code counted = true}), so a failed answer, which un-counts its row, is free.
 */
@Service
@RequiredArgsConstructor
public class AssistantAccessService {

    static final String TIER_PRO = "PRO";
    static final String TIER_FREE = "FREE";

    private final BusinessConfigService businessConfig;
    private final BetaAccessService betaAccess;
    private final AiTripRepository subscriptions;
    private final AssistantRepository repository;
    private final UserTimeZones userTimeZones;

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

    public int dailyLimit(UUID userId) {
        return TIER_PRO.equals(tier(userId))
                ? businessConfig.getInt(BusinessConfigKey.ASSISTANT_PRO_DAILY_QUESTIONS)
                : businessConfig.getInt(BusinessConfigKey.ASSISTANT_FREE_DAILY_QUESTIONS);
    }

    public int usedToday(UUID userId) {
        return (int) repository.countCountedTurnsSince(userId, startOfToday(userId));
    }

    public AssistantMeResponse me(UUID userId) {
        return AssistantMeResponse.builder()
                .available(isAvailable(userId))
                .dailyLimit(dailyLimit(userId))
                .usedToday(usedToday(userId))
                .tier(tier(userId))
                .botUserId(AssistantIdentity.BOT_USER_ID)
                .name(AssistantIdentity.NAME)
                .username(AssistantIdentity.USERNAME)
                .build();
    }

    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** The user's local midnight, expressed in the clock's zone (the zone timestamps are written in). */
    LocalDateTime startOfToday(UUID userId) {
        ZoneId zone = userTimeZones.zoneFor(userId);
        ZonedDateTime localMidnight = ZonedDateTime.now(clock.withZone(zone)).truncatedTo(ChronoUnit.DAYS);
        return localMidnight.withZoneSameInstant(clock.getZone()).toLocalDateTime();
    }
}

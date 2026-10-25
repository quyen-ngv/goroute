package com.ds.goroute.feed;

import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Who gets the ranked feed (D7). USER.BETA_USER always does; everyone does once
 * FEED.RANKING_ENABLED is on; before that, FEED.RANKING_PERCENT of signed-in users, chosen by a
 * stable hash of their id so a person does not flip between feeds.
 */
@Component
@RequiredArgsConstructor
public class FeedRollout {

    public static final String RANKED = "ranked";
    public static final String LEGACY = "legacy";

    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;

    public boolean enabledFor(UUID viewerId) {
        if (config.getBoolean(BusinessConfigKey.FEED_RANKING_ENABLED)) {
            return true;
        }
        if (viewerId == null) {
            return false;
        }
        if (betaAccess.isBetaUser(viewerId)) {
            return true;
        }
        int percent = config.getInt(BusinessConfigKey.FEED_RANKING_PERCENT);
        return percent > 0 && bucket(viewerId) < percent;
    }

    public boolean isBeta(UUID viewerId) {
        return viewerId != null && betaAccess.isBetaUser(viewerId);
    }

    /** 0..99, stable per user. */
    static int bucket(UUID viewerId) {
        long mixed = viewerId.getMostSignificantBits() ^ (viewerId.getLeastSignificantBits() * 31);
        return (int) Math.floorMod(mixed, 100L);
    }
}

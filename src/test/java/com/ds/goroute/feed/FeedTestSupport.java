package com.ds.goroute.feed;

import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Builders shared by the Feed ranking tests. */
public final class FeedTestSupport {

    private FeedTestSupport() {
    }

    /** The shipped defaults with {@code overrideJson} laid over them. */
    public static FeedWeights weights(String overrideJson) {
        BusinessConfigService config = mock(BusinessConfigService.class);
        when(config.getText(BusinessConfigKey.FEED_RANKING_WEIGHTS)).thenReturn(overrideJson);
        return new FeedWeightsProvider(config, new ObjectMapper()).get();
    }

    public static FeedWeights defaults() {
        return weights("{}");
    }

    public static FeedItemRow checkin(UUID author, LocalDateTime publishedAt) {
        return row("CHECKIN", author, publishedAt);
    }

    public static FeedItemRow review(UUID author, LocalDateTime publishedAt) {
        return row("REVIEW", author, publishedAt);
    }

    public static FeedItemRow trip(UUID author, LocalDateTime publishedAt) {
        return row("TRIP", author, publishedAt);
    }

    public static FeedItemRow row(String type, UUID author, LocalDateTime publishedAt) {
        FeedItemRow row = new FeedItemRow();
        row.setItemType(type);
        row.setItemId(UUID.randomUUID());
        row.setAuthorId(author);
        row.setPublishedAt(publishedAt);
        row.setAuthorTrust(50);
        row.setEngagementScore(1);
        row.setQualityScore(1);
        row.setEligible(true);
        row.setRecommendOk(true);
        row.setSubTypes(new String[0]);
        return row;
    }
}

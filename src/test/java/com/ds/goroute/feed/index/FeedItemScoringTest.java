package com.ds.goroute.feed.index;

import com.ds.goroute.feed.FeedTestSupport;
import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.model.FeedItemRow;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FeedItemScoringTest {

    private final FeedWeights w = FeedTestSupport.defaults();
    private final LocalDateTime now = LocalDateTime.now();

    private FeedItemRow checkin() {
        return FeedTestSupport.checkin(UUID.randomUUID(), now);
    }

    private FeedItemRow trip() {
        return FeedTestSupport.trip(UUID.randomUUID(), now);
    }

    @Nested
    class Quality {

        @Test
        void aBareCheckinIsNeutral() {
            assertThat(FeedItemScoring.quality(checkin(), w)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void photosTextAndAPlaceEachAdd() {
            FeedItemRow rich = checkin();
            rich.setHasPhoto(true);
            rich.setPhotoCount(5);
            rich.setTextLen(120);
            rich.setPlaceId(UUID.randomUUID());

            // 1 + 0.3 photo + 0.1 x min(5, 3) + 0.1 text + 0.2 place
            assertThat(FeedItemScoring.quality(rich, w)).isCloseTo(1.9, within(1e-9));
        }

        @Test
        void trustMovesQualityByTenPercentEitherWay() {
            FeedItemRow trusted = checkin();
            trusted.setAuthorTrust(100);
            FeedItemRow distrusted = checkin();
            distrusted.setAuthorTrust(0);

            assertThat(FeedItemScoring.quality(trusted, w)).isCloseTo(1.1, within(1e-9));
            assertThat(FeedItemScoring.quality(distrusted, w)).isCloseTo(0.9, within(1e-9));
        }

        @Test
        void aGroupCanOverrideAQualityWeight() {
            FeedWeights stays = FeedTestSupport.weights(
                    "{\"quality\":{\"byGroup\":{\"ACCOMMODATION\":{\"text40\":0.5}}}}");
            FeedItemRow hotel = checkin();
            hotel.setPlaceGroup("ACCOMMODATION");
            hotel.setTextLen(80);
            FeedItemRow cafe = checkin();
            cafe.setPlaceGroup("FOOD_AND_DRINK");
            cafe.setTextLen(80);

            assertThat(FeedItemScoring.quality(hotel, stays)).isCloseTo(1.5, within(1e-9));
            assertThat(FeedItemScoring.quality(cafe, stays)).isCloseTo(1.1, within(1e-9));
        }

        @Test
        void aTripWithoutAnyPictureIsPenalisedAndAFullOneRewarded() {
            FeedItemRow empty = trip();
            empty.setStopCount(1);
            FeedItemRow full = trip();
            full.setPhotoCount(10);
            full.setStopCount(12);
            full.setLinkedStopCount(10);
            full.setDayCount(3);
            full.setTextLen(200);
            full.setHasCost(true);
            full.setCopyCount(5);

            // 1 + 0.05 stop - 0.3 no picture
            assertThat(FeedItemScoring.quality(empty, w)).isCloseTo(0.75, within(1e-9));
            // 1 + 0.6 + 0.6 + 0.15 + 0.1 + 0.1 + 0.1 + 0.2
            assertThat(FeedItemScoring.quality(full, w)).isCloseTo(2.85, within(1e-9));
        }

        @Test
        void aCoverSparesATripThePicturePenalty() {
            FeedItemRow covered = trip();
            covered.setHasCover(true);

            assertThat(FeedItemScoring.quality(covered, w)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void qualityNeverDropsBelowAFloor() {
            FeedItemRow awful = trip();
            awful.setAuthorTrust(0);
            FeedWeights harsh = FeedTestSupport.weights("{\"quality\":{\"trip\":{\"noImagePenalty\":-5}}}");

            assertThat(FeedItemScoring.quality(awful, harsh)).isEqualTo(0.3);
        }
    }

    @Nested
    class Engagement {

        @Test
        void noInteractionAndNoImpressionIsNeutral() {
            assertThat(FeedItemScoring.engagement(checkin(), w)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void rarerInteractionsWeighMore() {
            FeedItemRow liked = checkin();
            liked.setLikeCount(6);
            FeedItemRow saved = checkin();
            saved.setSaveCount(1);
            FeedItemRow commented = checkin();
            commented.setCommentCount(1);

            assertThat(FeedItemScoring.engagement(saved, w))
                    .isCloseTo(FeedItemScoring.engagement(liked, w), within(1e-9));
            assertThat(FeedItemScoring.engagement(commented, w))
                    .isLessThan(FeedItemScoring.engagement(saved, w));
        }

        @Test
        void manyImpressionsWithFewLikesFallBelowFewImpressionsWithSome() {
            FeedItemRow overexposed = checkin();
            overexposed.setImpressionCount(1000);
            overexposed.setLikeCount(10);
            FeedItemRow promising = checkin();
            promising.setImpressionCount(20);
            promising.setLikeCount(5);

            assertThat(FeedItemScoring.engagement(promising, w))
                    .isGreaterThan(FeedItemScoring.engagement(overexposed, w));
        }

        @Test
        void engagementIsCapped() {
            FeedItemRow viral = checkin();
            viral.setLikeCount(100_000);

            assertThat(FeedItemScoring.engagement(viral, w)).isEqualTo(6.0);
        }
    }

    @Test
    void reportsWeighMoreThanHides() {
        FeedItemRow hidden = checkin();
        hidden.setHideCount(1);
        FeedItemRow reported = checkin();
        reported.setReportCount(1);

        assertThat(FeedItemScoring.negative(checkin(), w)).isEqualTo(1.0);
        assertThat(FeedItemScoring.negative(reported, w)).isLessThan(FeedItemScoring.negative(hidden, w));
    }

    @Test
    void theStaticScoreIsEngagementTimesQualityTimesNegative() {
        FeedItemRow row = checkin();
        row.setLikeCount(3);
        row.setHasPhoto(true);
        row.setHideCount(1);

        assertThat(FeedItemScoring.staticScore(row, w)).isCloseTo(
                FeedItemScoring.engagement(row, w) * FeedItemScoring.quality(row, w) * FeedItemScoring.negative(row, w),
                within(1e-9));
    }

    @Nested
    class RecommendationGate {

        @Test
        void checkinsAndReviewsAlwaysPass() {
            assertThat(FeedItemScoring.recommendOk(checkin(), w)).isTrue();
        }

        @Test
        void aTripPassesWithAPhotoOrEnoughStops() {
            FeedItemRow photo = trip();
            photo.setPhotoCount(1);
            FeedItemRow stops = trip();
            stops.setStopCount(3);
            FeedItemRow described = trip();
            described.setStopCount(2);
            described.setTextLen(80);

            assertThat(FeedItemScoring.recommendOk(photo, w)).isTrue();
            assertThat(FeedItemScoring.recommendOk(stops, w)).isTrue();
            assertThat(FeedItemScoring.recommendOk(described, w)).isTrue();
        }

        @Test
        void anEmptyTripDoesNotPass() {
            FeedItemRow twoStops = trip();
            twoStops.setStopCount(2);
            twoStops.setTextLen(79);

            assertThat(FeedItemScoring.recommendOk(trip(), w)).isFalse();
            assertThat(FeedItemScoring.recommendOk(twoStops, w)).isFalse();
        }
    }
}

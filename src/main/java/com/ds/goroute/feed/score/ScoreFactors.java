package com.ds.goroute.feed.score;

import com.ds.goroute.feed.FeedCandidate;
import com.ds.goroute.feed.FeedTypes;
import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.ViewerContext;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedViewerRows;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The factors of plan §4.3: score = (E x Q x N) x F x A x G x S x Seen.
 * E, Q and N do not depend on the viewer and are stored on the row; the rest are per request.
 */
public final class ScoreFactors {

    private ScoreFactors() {
    }

    /** E x Q x N, computed by FeedIndexJob and stored as feed_items.engagement_score. */
    @Component
    @Order(10)
    public static class Stored implements ScoreFactor {
        @Override
        public String name() {
            return "EQN";
        }

        @Override
        public double apply(FeedCandidate candidate, Request request) {
            double stored = candidate.row().getEngagementScore();
            return stored > 0 ? stored : 1.0;
        }
    }

    /**
     * F: freshness, halved every half-life (48 h for check-ins and reviews, a week for trips:
     * an itinerary stays useful longer). Never below the floor, and a post matching a strong
     * context (around here, on the itinerary) keeps a higher floor: last year's review of the
     * café next door is still worth reading.
     */
    @Component
    @Order(20)
    public static class Freshness implements ScoreFactor {
        @Override
        public String name() {
            return "F";
        }

        @Override
        public double apply(FeedCandidate candidate, Request request) {
            FeedWeights w = request.weights();
            FeedItemRow row = candidate.row();
            double halfLifeHours = switch (row.type()) {
                case CHECKIN -> w.d("freshness.halfLifeHoursCheckin", 48);
                case REVIEW -> w.d("freshness.halfLifeHoursReview", 48);
                case TRIP -> w.d("freshness.halfLifeHoursTrip", 168);
            };
            double ageHours = Math.max(0, Duration.between(row.getPublishedAt(), request.now()).toMinutes() / 60.0);
            double decay = Math.pow(2, -ageHours / Math.max(1, halfLifeHours));
            double floor = candidate.contextBoost() >= 0.6
                    ? w.d("freshness.contextFloor", 0.5)
                    : w.d("freshness.floor", 0.15);
            return Math.max(floor, decay);
        }
    }

    /** A: following the author, being followed back, and the nightly affinity. */
    @Component
    @Order(30)
    public static class Affinity implements ScoreFactor {
        @Override
        public String name() {
            return "A";
        }

        @Override
        public double apply(FeedCandidate candidate, Request request) {
            ViewerContext context = request.context();
            FeedWeights w = request.weights();
            Boolean mutual = context.getFollowing().get(candidate.row().getAuthorId());
            double a = 1.0;
            if (mutual != null) {
                a += w.d("affinity.follow", 1.0);
                if (mutual) {
                    a += w.d("affinity.mutual", 0.3);
                }
            }
            Float affinity = context.getAffinity().get(candidate.row().getAuthorId());
            if (affinity != null && affinity > 0) {
                a += w.d("affinity.logScale", 0.5) * Math.log1p(affinity);
            }
            return a;
        }
    }

    /**
     * G: the viewer's context (plan §4.9). The strongest match counts, not the sum. A review
     * near the phone gets a little more than a check-in there: it says what the place is like.
     */
    @Component
    @Order(40)
    public static class Context implements ScoreFactor {
        @Override
        public String name() {
            return "G";
        }

        @Override
        public double apply(FeedCandidate candidate, Request request) {
            double g = 1 + candidate.contextBoost();
            if (candidate.nearDevice() && candidate.row().type() == com.ds.goroute.type.FeedItemType.REVIEW) {
                g *= request.weights().d("context.nearReviewBoost", 1.2);
            }
            return g;
        }
    }

    /** S: how many of the people the viewer follows engaged with the post lately. */
    @Component
    @Order(50)
    public static class Social implements ScoreFactor {
        @Override
        public String name() {
            return "S";
        }

        @Override
        public double apply(FeedCandidate candidate, Request request) {
            FeedWeights w = request.weights();
            int people = Math.min(candidate.socialPeople(), w.i("social.maxFollowers", 3));
            return 1 + w.d("social.perFollower", 0.2) * people;
        }
    }

    /**
     * Seen: a post the viewer already saw goes down, one they opened further. Under a chip the
     * viewer is browsing a topic, so the push is gentler. From fill level L3, posts seen
     * repeatedly come back too, far down (the ranking service let them through).
     */
    @Component
    @Order(60)
    public static class Seen implements ScoreFactor {
        @Override
        public String name() {
            return "seen";
        }

        @Override
        public double apply(FeedCandidate candidate, Request request) {
            FeedViewerRows.Seen seen = candidate.seen();
            if (seen == null) {
                return 1.0;
            }
            FeedWeights w = request.weights();
            if (request.spec().narrowed()) {
                return w.d("seen.chipFactor", 0.7);
            }
            double factor = seen.isOpened() ? w.d("seen.openedFactor", 0.15) : w.d("seen.onceFactor", 0.25);
            if (request.level().atLeast(FeedTypes.FillLevel.L3) && repeatedlySeen(seen, request)) {
                factor *= w.d("fillLadder.seenPenalty", 0.3);
            }
            return factor;
        }

        /** Seen at least seen.maxTimesInWindow times, the last within seen.excludeWindowDays. */
        public static boolean repeatedlySeen(FeedViewerRows.Seen seen, Request request) {
            FeedWeights w = request.weights();
            return seen.getSeenCount() >= w.i("seen.maxTimesInWindow", 2)
                    && seen.getLastSeenAt() != null
                    && seen.getLastSeenAt().isAfter(request.now().minusDays(w.i("seen.excludeWindowDays", 3)));
        }
    }
}

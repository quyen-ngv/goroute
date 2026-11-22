package com.ds.goroute.feed.backend;

import com.ds.goroute.feed.index.FeedLuceneIndex;
import com.ds.goroute.feed.model.FeedCandidateQuery;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedKey;
import com.ds.goroute.mapper.FeedItemMapper;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Candidate plans on the Lucene feed index (plan §6.2, step 3), in two steps:
 *
 * <ol>
 *   <li>Lucene does the expensive part: the selector (authors, provinces, a point, keys), the
 *       window, the gates and the order, over every post, stopping after the top
 *       {@code limit} plus a small margin.
 *   <li>Postgres re-runs the very same {@code selectCandidates} on just those keys (a primary-key
 *       read), which applies the viewer's blocks, hides and joined trips as they are this
 *       instant, re-checks every filter against the live row, orders and cuts to {@code limit}.
 * </ol>
 *
 * So a candidate is exactly what SQL would have returned among the posts Lucene found; what can
 * differ is only which posts get found when the index lags a moment, or when the viewer's
 * exclusions remove more than the margin. Plans this backend cannot express (itinerary places,
 * place-kind chips) are left to SQL by {@link #supports}.
 */
@Component
@RequiredArgsConstructor
public class LuceneCandidateBackend implements CandidateBackend {

    /** Extra posts read so the viewer's exclusions rarely leave a source short. */
    static final int MIN_MARGIN = 20;

    private static final Sort NEWEST = new Sort(new SortField(FeedLuceneIndex.PUBLISHED, SortField.Type.LONG, true));
    private static final Sort MOST_ENGAGING = new Sort(
            new SortField(FeedLuceneIndex.ENGAGEMENT, SortField.Type.FLOAT, true),
            new SortField(FeedLuceneIndex.PUBLISHED, SortField.Type.LONG, true));

    private final FeedLuceneIndex index;
    private final FeedItemMapper itemMapper;

    @Override
    public String name() {
        return "lucene";
    }

    /** The index has been built and the plan uses only what it holds. */
    public boolean supports(FeedCandidateQuery plan) {
        return index.ready()
                && plan.getPlaceIds() == null
                && empty(plan.getPlaceGroups())
                && empty(plan.getSubTypes());
    }

    @Override
    public List<FeedItemRow> execute(FeedCandidateQuery plan) {
        Query query = query(plan);
        if (query == null) {
            return List.of();
        }
        int fetch = plan.getLimit() + Math.max(MIN_MARGIN, plan.getLimit() / 10);
        List<FeedKey> keys = index.search(query, plan.isOrderByEngagement() ? MOST_ENGAGING : NEWEST, fetch);
        if (keys.isEmpty()) {
            return List.of();
        }
        // The selectors are already applied; the keys replace them.
        return itemMapper.selectCandidates(plan.toBuilder()
                .authorIds(null)
                .provinceCodes(null)
                .nearLat(null)
                .nearLng(null)
                .nearMeters(null)
                .keys(keys)
                .build());
    }

    /** The plan as a Lucene query; null when a selector is an empty list (nothing can match). */
    static Query query(FeedCandidateQuery plan) {
        BooleanQuery.Builder q = new BooleanQuery.Builder()
                .add(new MatchAllDocsQuery(), Occur.MUST)
                .add(flag(FeedLuceneIndex.ELIGIBLE), Occur.FILTER);
        if (!plan.isIgnoreRecommendGate()) {
            q.add(flag(plan.isRelaxedRecommendGate() ? FeedLuceneIndex.RELAXED : FeedLuceneIndex.RECOMMEND), Occur.FILTER);
        }
        if (plan.getViewerId() != null) {
            q.add(new TermQuery(new Term(FeedLuceneIndex.AUTHOR, plan.getViewerId().toString())), Occur.MUST_NOT);
        }
        if (!empty(plan.getItemTypes())) {
            q.add(terms(FeedLuceneIndex.TYPE, plan.getItemTypes(), Function.identity()), Occur.FILTER);
        }
        if (plan.getRadiusLat() != null && plan.getRadiusLng() != null && plan.getRadiusMeters() != null) {
            q.add(LatLonPoint.newDistanceQuery(FeedLuceneIndex.POINT,
                    plan.getRadiusLat(), plan.getRadiusLng(), plan.getRadiusMeters()), Occur.FILTER);
        }
        if (plan.getAuthorIds() != null) {
            if (plan.getAuthorIds().isEmpty()) {
                return null;
            }
            q.add(terms(FeedLuceneIndex.AUTHOR, plan.getAuthorIds(), Object::toString), Occur.FILTER);
        }
        if (plan.getProvinceCodes() != null) {
            if (plan.getProvinceCodes().isEmpty()) {
                return null;
            }
            q.add(terms(FeedLuceneIndex.PROVINCE, plan.getProvinceCodes(), Function.identity()), Occur.FILTER);
        }
        if (plan.getNearLat() != null && plan.getNearLng() != null && plan.getNearMeters() != null) {
            q.add(LatLonPoint.newDistanceQuery(FeedLuceneIndex.POINT,
                    plan.getNearLat(), plan.getNearLng(), plan.getNearMeters()), Occur.FILTER);
        }
        if (plan.getKeys() != null) {
            if (plan.getKeys().isEmpty()) {
                return null;
            }
            q.add(terms(FeedLuceneIndex.KEY, plan.getKeys(), FeedKey::key), Occur.FILTER);
        }
        if (plan.getSince() != null) {
            // published_at > since, as in SQL.
            q.add(LongPoint.newRangeQuery(FeedLuceneIndex.PUBLISHED,
                    FeedLuceneIndex.millis(plan.getSince()) + 1, Long.MAX_VALUE), Occur.FILTER);
        }
        if (plan.getMaxImpressions() != null) {
            q.add(IntPoint.newRangeQuery(FeedLuceneIndex.IMPRESSIONS,
                    Integer.MIN_VALUE, plan.getMaxImpressions() - 1), Occur.FILTER);
        }
        return q.build();
    }

    private static Query flag(String value) {
        return new TermQuery(new Term(FeedLuceneIndex.FLAGS, value));
    }

    private static <T> Query terms(String field, Collection<T> values, Function<T, String> asText) {
        return new TermInSetQuery(field, values.stream().map(asText).distinct().map(BytesRef::new).toList());
    }

    private static boolean empty(Collection<?> values) {
        return values == null || values.isEmpty();
    }
}

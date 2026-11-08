package com.ds.goroute.feed.session;

import com.ds.goroute.dto.response.FeedForYouPageResponse;
import com.ds.goroute.feed.FeedTypes;
import com.ds.goroute.feed.model.FeedKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One ranking, kept while the viewer scrolls it (design §3.5): pages are slices of
 * {@link #ranked}, so the order never moves under the viewer and nothing is scored twice.
 *
 * @param servedBefore every post earlier sessions of this scroll already handed out, so a
 *                     continuation never repeats one
 * @param explain      score breakdown by "TYPE:id"; only kept for debug requests
 * @param featureNames the columns of {@link #features}: the factor names, then "score"
 * @param features     the factor values and score of every ranked post, in {@link #ranked}
 *                     order, as float16 (Float.floatToFloat16) in one flat array of
 *                     ranked.size() x featureNames.size(); logged with the post when a page
 *                     serves it (feed_events SERVE)
 */
public record FeedSession(
        String id,
        UUID viewerId,
        String filterKey,
        List<FeedKey> ranked,
        Set<String> servedBefore,
        FeedTypes.FillLevel fillLevel,
        FeedForYouPageResponse.Context context,
        boolean debug,
        Map<String, FeedForYouPageResponse.Rank> explain,
        List<String> featureNames,
        short[] features) {

    /** The features of the post at {@code index} of {@link #ranked}, by name. */
    public Map<String, Float> featuresAt(int index) {
        int width = featureNames.size();
        Map<String, Float> values = new LinkedHashMap<>();
        if (width == 0 || (index + 1) * width > features.length) {
            return values;
        }
        for (int i = 0; i < width; i++) {
            values.put(featureNames.get(i), Float.float16ToFloat(features[index * width + i]));
        }
        return values;
    }

    /** The ranking was cut at its size cap, so there may be more to rank after it. */
    public boolean full(int sessionSize) {
        return ranked.size() >= sessionSize;
    }
}

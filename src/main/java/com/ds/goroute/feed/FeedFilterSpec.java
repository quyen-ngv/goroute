package com.ds.goroute.feed;

import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.type.FeedItemType;

import java.util.List;
import java.util.Locale;

/**
 * What the request narrowed the feed to: the Feed's chips. Parsed once per request and
 * applied in the WHERE of every candidate source, so a chip shrinks the pool rather than
 * filtering a ranked page.
 *
 * @param itemTypes   kinds of post; empty means all three
 * @param kind        place-group / sub-type chip, or null
 * @param geo         the "near me" chip's hard radius, or null
 * @param tripsChip   the Trips chip: trips only, without the recommendation gate
 */
public record FeedFilterSpec(List<FeedItemType> itemTypes, FeedPlaceKind kind, FeedGeoFilter geo, boolean tripsChip) {

    public static FeedFilterSpec of(List<FeedItemType> types, FeedPlaceKind kind, FeedGeoFilter geo) {
        List<FeedItemType> distinct = types == null ? List.of() : types.stream().distinct().sorted().toList();
        boolean tripsOnly = distinct.size() == 1 && distinct.get(0) == FeedItemType.TRIP;
        // A place chip shows posts about a place; trips under it are only the themed ones.
        return new FeedFilterSpec(distinct, kind, geo, tripsOnly && kind == null);
    }

    public boolean hasPlaceKind() {
        return kind != null;
    }

    /** Any chip at all: the viewer is browsing a topic rather than scrolling the feed. */
    public boolean narrowed() {
        return kind != null || geo != null || !itemTypes.isEmpty();
    }

    /** Only one kind of post can come back, so "do not repeat the kind" means nothing. */
    public boolean singleType() {
        return itemTypes.size() == 1;
    }

    public List<String> itemTypeNames() {
        return itemTypes.stream().map(Enum::name).toList();
    }

    /** Part of the session key: a different chip is a different feed. */
    public String key() {
        return String.join("|",
                String.join(",", itemTypeNames()),
                kind == null ? "" : String.valueOf(kind.getPlaceGroups()) + String.valueOf(kind.getSubTypes()),
                geo == null ? "" : String.format(Locale.ROOT, "%.3f,%.3f,%s",
                        geo.latitude().doubleValue(), geo.longitude().doubleValue(), geo.radiusKm().toPlainString()));
    }
}

package com.ds.goroute.feed;

import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedKey;
import com.ds.goroute.feed.model.FeedViewerRows;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** One post on its way through a ranking: where it came from, its context match and score. */
public final class FeedCandidate {

    private final FeedItemRow row;
    private final FeedKey key;
    private final Set<FeedTypes.SourceKind> sources = EnumSet.noneOf(FeedTypes.SourceKind.class);

    /** g of the context factor: the strongest context this post matches, 0 for none. */
    private double contextBoost;
    /** Which context gave {@link #contextBoost}, for the debug output and the re-rank. */
    private String contextReason;
    private boolean nearDevice;
    private int socialPeople;
    private FeedViewerRows.Seen seen;
    private double score;
    private final Map<String, Double> explain = new LinkedHashMap<>();

    public FeedCandidate(FeedItemRow row) {
        this.row = row;
        this.key = row.key();
    }

    public FeedItemRow row() {
        return row;
    }

    public FeedKey key() {
        return key;
    }

    public String keyString() {
        return key.key();
    }

    public Set<FeedTypes.SourceKind> sources() {
        return sources;
    }

    public boolean from(FeedTypes.SourceKind kind) {
        return sources.contains(kind);
    }

    public double contextBoost() {
        return contextBoost;
    }

    public String contextReason() {
        return contextReason;
    }

    public void context(double boost, String reason) {
        if (boost > contextBoost) {
            contextBoost = boost;
            contextReason = reason;
        }
    }

    public boolean nearDevice() {
        return nearDevice;
    }

    public void nearDevice(boolean value) {
        nearDevice = value;
    }

    public int socialPeople() {
        return socialPeople;
    }

    public void socialPeople(int value) {
        socialPeople = value;
    }

    public FeedViewerRows.Seen seen() {
        return seen;
    }

    public void seen(FeedViewerRows.Seen value) {
        seen = value;
    }

    public double score() {
        return score;
    }

    public void score(double value) {
        score = value;
    }

    public Map<String, Double> explain() {
        return explain;
    }

    /** No memory photos: the card shows the city's stock picture. */
    public boolean photolessTrip() {
        return row.isTrip() && row.getPhotoCount() == 0;
    }
}

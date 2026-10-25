package com.ds.goroute.feed;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The ranking's knobs: {@code feed/feed-ranking-defaults.json} with FEED.RANKING_WEIGHTS laid
 * over it. Read by dotted path, so a new factor or rule adds a key without a new class, and a
 * value missing from both falls back to the caller's default rather than failing a request.
 */
public final class FeedWeights {

    private final JsonNode root;

    FeedWeights(JsonNode root) {
        this.root = root;
    }

    public double d(String path, double fallback) {
        JsonNode node = at(path);
        return node != null && node.isNumber() ? node.asDouble() : fallback;
    }

    public int i(String path, int fallback) {
        JsonNode node = at(path);
        return node != null && node.isNumber() ? node.asInt() : fallback;
    }

    public boolean b(String path, boolean fallback) {
        JsonNode node = at(path);
        return node != null && node.isBoolean() ? node.asBoolean() : fallback;
    }

    /** A sub-tree, for per-group overrides; null when absent. */
    public JsonNode node(String path) {
        return at(path);
    }

    public int version() {
        return i("version", 1);
    }

    private JsonNode at(String path) {
        JsonNode node = root;
        for (String part : path.split("\\.")) {
            if (node == null) {
                return null;
            }
            node = node.get(part);
        }
        return node == null || node.isMissingNode() || node.isNull() ? null : node;
    }
}

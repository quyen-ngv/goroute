package com.ds.goroute.feed;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

/**
 * One Feed surface as configured (plan §3.3): which candidate sources and score factors it runs.
 * Read from the ranking weights under {@code surfaces.<name>.sources} and
 * {@code surfaces.<name>.factors}; a list left out means every source or factor bean, so
 * for-you without configuration runs exactly what it ran before surfaces existed.
 *
 * @param sources source kind names, or null for all
 * @param factors factor names, or null for all
 */
public record SurfaceSpec(String name, Set<String> sources, Set<String> factors) {

    public static final String FOR_YOU = "for-you";

    public static SurfaceSpec of(String name, FeedWeights w) {
        return new SurfaceSpec(name,
                names(w.node("surfaces." + name + ".sources")),
                names(w.node("surfaces." + name + ".factors")));
    }

    public boolean runs(FeedTypes.SourceKind kind) {
        return sources == null || sources.contains(kind.name());
    }

    public boolean scores(String factor) {
        return factors == null || factors.contains(factor);
    }

    private static Set<String> names(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        Set<String> names = new HashSet<>();
        node.forEach(entry -> names.add(entry.asText()));
        return Set.copyOf(names);
    }
}

package com.ds.goroute.feed;

import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

/**
 * Serves {@link FeedWeights}: the defaults shipped with the code, with the operator's
 * FEED.RANKING_WEIGHTS JSON merged over them key by key. Re-read at most once a minute. An
 * override that is not valid JSON is logged and ignored, keeping the last good weights.
 */
@Component
@Slf4j
public class FeedWeightsProvider {

    static final String DEFAULTS = "feed/feed-ranking-defaults.json";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final BusinessConfigService config;
    private final ObjectMapper objectMapper;
    private final JsonNode defaults;

    private volatile FeedWeights current;
    private volatile String currentOverride;
    private volatile Instant loadedAt = Instant.EPOCH;

    public FeedWeightsProvider(BusinessConfigService config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.defaults = readDefaults(objectMapper);
        this.current = new FeedWeights(defaults);
    }

    public FeedWeights get() {
        if (Instant.now().isAfter(loadedAt.plus(TTL))) {
            reload();
        }
        return current;
    }

    private synchronized void reload() {
        if (!Instant.now().isAfter(loadedAt.plus(TTL))) {
            return;
        }
        loadedAt = Instant.now();
        String override;
        try {
            override = config.getText(BusinessConfigKey.FEED_RANKING_WEIGHTS);
        } catch (RuntimeException exception) {
            log.warn("Feed ranking weights unreadable, keeping the current ones: {}", exception.getMessage());
            return;
        }
        if (override != null && override.equals(currentOverride)) {
            return;
        }
        try {
            JsonNode merged = defaults.deepCopy();
            if (override != null && !override.isBlank()) {
                JsonNode overrideNode = objectMapper.readTree(override);
                if (overrideNode != null && overrideNode.isObject()) {
                    merge((ObjectNode) merged, overrideNode);
                }
            }
            current = new FeedWeights(merged);
            currentOverride = override;
        } catch (IOException exception) {
            log.error("FEED.RANKING_WEIGHTS is not valid JSON, keeping the last good weights: {}",
                    exception.getMessage());
            currentOverride = override;
        }
    }

    private static void merge(ObjectNode target, JsonNode override) {
        Iterator<Map.Entry<String, JsonNode>> fields = override.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode existing = target.get(field.getKey());
            if (existing instanceof ObjectNode existingObject && field.getValue().isObject()) {
                merge(existingObject, field.getValue());
            } else {
                target.set(field.getKey(), field.getValue());
            }
        }
    }

    private static JsonNode readDefaults(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(DEFAULTS).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException exception) {
            throw new IllegalStateException("Missing " + DEFAULTS, exception);
        }
    }
}

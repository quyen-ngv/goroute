package com.ds.goroute.service;

import com.ds.goroute.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialLocationConfigService {
    private static final String LABEL = "SOCIAL_LOCATION";

    private final AppConfigRepository repository;

    public int dailyJobLimit() { return positive("DAILY_JOB_LIMIT_DEFAULT", 5); }
    public int maxConcurrentJobs() { return positive("MAX_CONCURRENT_JOBS", 5); }
    public int maxQueuedJobs() { return positive("MAX_QUEUED_JOBS", 100); }
    public int maxVideoSeconds(String tier) {
        return "PRO".equalsIgnoreCase(tier)
                ? positive("MAX_VIDEO_SECONDS_PRO", 300)
                : positive("MAX_VIDEO_SECONDS_DEFAULT", 180);
    }
    public int frameIntervalSeconds() { return positive("FRAME_INTERVAL_SECONDS", 3); }
    public int imageMaxWidth() { return positive("IMAGE_MAX_WIDTH", 320); }
    public int imageJpegQuality() { return bounded("IMAGE_JPEG_QUALITY", 18, 2, 31); }
    public int firstBlockMinutes() { return positive("FIRST_BLOCK_MINUTES", 10); }
    public int secondBlockHours() { return positive("SECOND_BLOCK_HOURS", 24); }
    public int permanentBlockStrikes() { return positive("PERMANENT_BLOCK_STRIKES", 3); }
    public String aiProvider() { return text("AI_PROVIDER", "DEEPSEEK").toUpperCase(); }
    public String aiModel() { return text("AI_MODEL", ""); }
    public String aiBaseUrl() { return text("AI_BASE_URL", ""); }

    private String text(String key, String fallback) {
        return repository.findActiveByLabelAndKey(LABEL, key)
                .map(config -> config.getValue() == null ? "" : config.getValue().trim())
                .filter(value -> !value.isEmpty())
                .orElse(fallback);
    }

    private int positive(String key, int fallback) {
        return bounded(key, fallback, 1, Integer.MAX_VALUE);
    }

    private int bounded(String key, int fallback, int min, int max) {
        String value = repository.findActiveByLabelAndKey(LABEL, key)
                .map(config -> config.getValue().trim())
                .orElse(null);
        if (value == null) return fallback;
        try {
            return Math.min(Math.max(Integer.parseInt(value), min), max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

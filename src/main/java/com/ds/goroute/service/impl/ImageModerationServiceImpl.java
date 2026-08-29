package com.ds.goroute.service.impl;

import com.ds.goroute.config.ImageModerationProperties;
import com.ds.goroute.entity.ImageModerationResult;
import com.ds.goroute.repository.ModerationAuditRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ImageModerationService;
import com.ds.goroute.service.moderation.ImageModerationProvider;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageModerationServiceImpl implements ImageModerationService {

    /**
     * Entry points that may run one notch looser. They still go through the check --
     * MOD-05 exists because "trusted, therefore skipped" is how a bypass is born.
     */
    private static final String ADMIN_ENTRY_PREFIX = "admin";

    private final ObjectProvider<ImageModerationProvider> providers;
    private final ModerationAuditRepository auditRepository;
    private final BusinessConfigService config;
    private final ImageModerationProperties properties;

    @Override
    public ModerationVerdict inspect(byte[] bytes, String contentType, UUID userId, String entryPoint) {
        if (!config.getBoolean(BusinessConfigKey.MODERATION_IMAGE_ENABLED)) {
            return ModerationVerdict.allowed();
        }
        ImageModerationProvider provider = providers.getIfAvailable();
        if (provider == null) {
            log.debug("Image moderation is enabled but no provider is configured");
            return ModerationVerdict.allowed();
        }

        Map<ModerationCategory, Double> scores;
        try {
            scores = provider.score(bytes, contentType);
        } catch (RuntimeException exception) {
            // Not a silent pass: the outage is recorded against the image and shows up in
            // the MOD-08 dashboard. Rejecting every upload during a provider outage would
            // take down photo upload for the whole product.
            log.warn("Image moderation provider {} unavailable: {}", provider.name(), exception.getMessage());
            record(bytes, userId, entryPoint, provider.name(), ModerationAction.LOG, null, null,
                    Map.<String, Object>of("error", "provider_unavailable"));
            return ModerationVerdict.allowed();
        }

        ModerationVerdict verdict = decide(scores, entryPoint);
        record(bytes, userId, entryPoint, provider.name(), verdict.action(), verdict.category(),
                verdict.category() == null ? null : scores.get(verdict.category()), asJsonMap(scores));
        return verdict;
    }

    /**
     * Applies the administrable threshold of the highest-scoring group. Rejection is
     * reserved for the groups the policy marks severe; everything else is published and
     * queued, because a false rejection of a beach photo in a travel app is a daily event
     * if thresholds are read the other way round.
     */
    private ModerationVerdict decide(Map<ModerationCategory, Double> scores, String entryPoint) {
        double relaxation = isRelaxed(entryPoint) ? properties.getRelaxedThresholdBonus() : 0d;
        ModerationVerdict verdict = ModerationVerdict.allowed();
        for (Map.Entry<ModerationCategory, Double> entry : scores.entrySet()) {
            Double threshold = thresholdFor(entry.getKey());
            if (threshold == null || entry.getValue() == null) {
                continue;
            }
            if (entry.getValue() < Math.min(1d, threshold + relaxation)) {
                continue;
            }
            ModerationAction action = entry.getKey().isSevere() ? ModerationAction.BLOCK : ModerationAction.FLAG;
            verdict = verdict.merge(ModerationVerdict.of(action, entry.getKey(), null,
                    entry.getKey().name() + " " + format(entry.getValue())));
        }
        return verdict;
    }

    private Double thresholdFor(ModerationCategory category) {
        BusinessConfigKey key = switch (category) {
            case SEXUAL -> BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_SEXUAL;
            case VIOLENCE -> BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_VIOLENCE;
            case DRUGS_WEAPONS -> BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_DRUGS_WEAPONS;
            case HATE_SYMBOL -> BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_HATE_SYMBOL;
            case SPAM -> BusinessConfigKey.MODERATION_IMAGE_THRESHOLD_SPAM;
            default -> null;
        };
        return key == null ? null : config.getDecimal(key);
    }

    private boolean isRelaxed(String entryPoint) {
        return entryPoint != null && entryPoint.startsWith(ADMIN_ENTRY_PREFIX);
    }

    private void record(byte[] bytes, UUID userId, String entryPoint, String provider,
                        ModerationAction action, ModerationCategory category, Double confidence,
                        Map<String, Object> rawScores) {
        try {
            auditRepository.insertImageResult(ImageModerationResult.builder()
                    .id(UUID.randomUUID())
                    .checksum(checksum(bytes))
                    .userId(userId)
                    .entryPoint(entryPoint)
                    .decision(action)
                    .category(category)
                    .confidence(confidence == null ? null
                            : BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP))
                    .provider(provider)
                    .rawScores(JsonUtils.toJson(rawScores))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException exception) {
            log.warn("Could not record image moderation result: {}", exception.getMessage());
        }
    }

    private Map<String, Object> asJsonMap(Map<ModerationCategory, Double> scores) {
        Map<String, Object> result = new LinkedHashMap<>();
        scores.forEach((category, value) -> result.put(category.name(), value));
        return result;
    }

    /** Lets the same rejected image be recognised across appeals and re-uploads. */
    private String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            return null;
        }
    }

    private String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

package com.ds.goroute.service.impl;

import com.ds.goroute.config.ImageModerationProperties;
import com.ds.goroute.entity.ImageModerationResult;
import com.ds.goroute.repository.ModerationAuditRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.HiddenImageService;
import com.ds.goroute.service.ImageModerationService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.moderation.ImageModerationProvider;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ImageModerationStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.ModerationFlagSource;
import com.ds.goroute.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
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
    private final ContentModerationService contentModerationService;
    private final HiddenImageService hiddenImageService;
    private final StorageService storageService;
    private final Executor executor;

    public ImageModerationServiceImpl(ObjectProvider<ImageModerationProvider> providers,
                                      ModerationAuditRepository auditRepository,
                                      BusinessConfigService config,
                                      ImageModerationProperties properties,
                                      ContentModerationService contentModerationService,
                                      HiddenImageService hiddenImageService,
                                      StorageService storageService,
                                      @Qualifier("applicationTaskExecutor") Executor executor) {
        this.providers = providers;
        this.auditRepository = auditRepository;
        this.config = config;
        this.properties = properties;
        this.contentModerationService = contentModerationService;
        this.hiddenImageService = hiddenImageService;
        this.storageService = storageService;
        this.executor = executor;
    }

    @Override
    public void submit(UUID userId, String entryPoint, String objectKey, String url,
                       byte[] bytes, String contentType) {
        if (!isActive()) {
            return;
        }
        ImageModerationResult pending = ImageModerationResult.builder()
                .id(UUID.randomUUID())
                .objectKey(objectKey)
                .url(url)
                .mimeType(contentType)
                .checksum(checksum(bytes))
                .userId(userId)
                .entryPoint(entryPoint)
                .status(ImageModerationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            auditRepository.insertImageResult(pending);
        } catch (RuntimeException exception) {
            // Without the row neither the worker's result nor a takedown has anything to
            // point at. The upload itself has already succeeded and must stay that way.
            log.warn("Could not record image {} for moderation: {}", objectKey, exception.getMessage(), exception);
            return;
        }
        try {
            executor.execute(() -> process(pending, bytes));
        } catch (RuntimeException exception) {
            // The row stays PENDING and the retry job reads the image back from storage.
            log.warn("Could not schedule moderation of image {}: {}", objectKey, exception.getMessage());
        }
    }

    @Override
    public int resumeStale(Duration olderThan, int limit) {
        var stale = auditRepository.findStalePendingImageResults(LocalDateTime.now().minus(olderThan), limit);
        for (ImageModerationResult image : stale) {
            try {
                byte[] bytes = image.getObjectKey() == null ? null : storageService.readObject(image.getObjectKey());
                if (bytes == null) {
                    complete(image, ModerationAction.LOG, null, null, null, Map.of("error", "object_missing"));
                    continue;
                }
                process(image, bytes);
            } catch (RuntimeException exception) {
                log.warn("Could not resume moderation of image {}: {}", image.getId(), exception.getMessage(), exception);
            }
        }
        return stale.size();
    }

    /** Scores one stored image and acts on the verdict. Runs off the request thread. */
    void process(ImageModerationResult image, byte[] bytes) {
        ImageModerationProvider provider = providers.getIfAvailable();
        if (!config.getBoolean(BusinessConfigKey.MODERATION_IMAGE_ENABLED) || provider == null) {
            complete(image, ModerationAction.ALLOW, null, null, null, Map.of("skipped", "moderation_off"));
            return;
        }

        Map<ModerationCategory, Double> scores;
        try {
            scores = provider.score(bytes, image.getMimeType());
        } catch (RuntimeException exception) {
            // Not a silent pass: the outage is recorded against the image and shows up in
            // the MOD-08 dashboard. Hiding every photo during a provider outage would take
            // photos down across the whole product.
            log.warn("Image moderation provider {} unavailable: {}", provider.name(), exception.getMessage(), exception);
            complete(image, ModerationAction.LOG, null, null, provider.name(),
                    Map.of("error", "provider_unavailable"));
            return;
        }

        ModerationVerdict verdict = decide(scores, image.getEntryPoint());
        boolean isRecorded = complete(image, verdict.action(), verdict.category(),
                verdict.category() == null ? null : scores.get(verdict.category()),
                provider.name(), asJsonMap(scores));
        if (!isRecorded || verdict.isAllowed()) {
            return;
        }

        contentModerationService.flag(ModeratedContentType.IMAGE, image.getId(), image.getUserId(),
                ModerationFlagSource.IMAGE_MODERATION, verdict, snapshot(image));
        if (verdict.blocks()) {
            hiddenImageService.hide(image, verdict.category());
        }
    }

    private boolean isActive() {
        return config.getBoolean(BusinessConfigKey.MODERATION_IMAGE_ENABLED) && providers.getIfAvailable() != null;
    }

    /**
     * Applies the administrable threshold of the highest-scoring group. Hiding is reserved
     * for the groups the policy marks severe; everything else stays up and is queued,
     * because a false hide of a beach photo in a travel app is a daily event if thresholds
     * are read the other way round.
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

    /** @return false when the row was not PENDING any more: another run already handled it */
    private boolean complete(ImageModerationResult image, ModerationAction action, ModerationCategory category,
                             Double confidence, String provider, Map<String, Object> rawScores) {
        try {
            return auditRepository.completeImageResult(ImageModerationResult.builder()
                    .id(image.getId())
                    .decision(action)
                    .category(category)
                    .confidence(confidence == null ? null
                            : BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP))
                    .provider(provider)
                    .rawScores(JsonUtils.toJson(rawScores))
                    .completedAt(LocalDateTime.now())
                    .build()) == 1;
        } catch (RuntimeException exception) {
            log.warn("Could not record image moderation result {}: {}", image.getId(), exception.getMessage(), exception);
            return false;
        }
    }

    /** What the reviewer needs in the queue: the image itself and where it came from. */
    private String snapshot(ImageModerationResult image) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("field", "image");
        snapshot.put("url", image.getUrl());
        snapshot.put("entryPoint", image.getEntryPoint());
        return JsonUtils.toJson(snapshot);
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

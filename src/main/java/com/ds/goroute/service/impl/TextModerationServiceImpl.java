package com.ds.goroute.service.impl;

import com.ds.goroute.entity.ModerationDecision;
import com.ds.goroute.repository.ModerationAuditRepository;
import com.ds.goroute.repository.ModerationTermRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.TextModerationService;
import com.ds.goroute.service.moderation.ModerationTermIndex;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationLayer;
import com.ds.goroute.type.ModerationStrictness;
import com.ds.goroute.type.ModerationVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class TextModerationServiceImpl implements TextModerationService {

    private static final int MATCHED_TEXT_LIMIT = 200;

    private final ModerationTermRepository termRepository;
    private final ModerationAuditRepository auditRepository;
    private final BusinessConfigService config;

    /**
     * The whole list lives in memory. With a few hundred entries this costs nothing and
     * removes a database round trip from every text field of every request; the reference
     * is swapped atomically so a refresh never exposes a half-built index.
     */
    private final AtomicReference<ModerationTermIndex> index = new AtomicReference<>();

    @Override
    public ModerationVerdict evaluate(ModeratedContentType contentType,
                                      String fieldLabel,
                                      ModerationVisibility visibility,
                                      String text,
                                      UUID userId) {
        ModerationVerdict verdict = preview(text, visibility);
        if (!verdict.isAllowed()) {
            record(contentType, fieldLabel, visibility, verdict, userId);
        }
        return verdict;
    }

    @Override
    public ModerationVerdict preview(String text, ModerationVisibility visibility) {
        if (text == null || text.isBlank() || !config.getBoolean(BusinessConfigKey.MODERATION_TEXT_FILTER_ENABLED)) {
            return ModerationVerdict.allowed();
        }
        ModerationStrictness strictness = strictnessFor(visibility);
        if (!strictness.runsKeywordLayer()) {
            return ModerationVerdict.allowed();
        }

        ModerationVerdict verdict = snapshot().evaluate(text);
        // Outside the fully strict tier a block becomes a flag, except for the groups
        // where even brief exposure is real damage (policy section 2).
        return strictness == ModerationStrictness.FULL ? verdict : verdict.downgradeUnlessSevere();
    }

    @Override
    public void refresh() {
        index.set(null);
    }

    /**
     * Resolves the configured strictness for a visibility tier. An unknown value falls
     * back to the code default rather than to "no filtering".
     */
    @Override
    public ModerationStrictness strictnessFor(ModerationVisibility visibility) {
        BusinessConfigKey key = switch (visibility) {
            case PUBLIC -> BusinessConfigKey.MODERATION_STRICTNESS_PUBLIC;
            case GROUP -> BusinessConfigKey.MODERATION_STRICTNESS_GROUP;
            case DIRECT -> BusinessConfigKey.MODERATION_STRICTNESS_DIRECT;
            case PRIVATE -> BusinessConfigKey.MODERATION_STRICTNESS_PRIVATE;
        };
        return config.getEnum(key, ModerationStrictness.class);
    }

    private ModerationTermIndex snapshot() {
        ModerationTermIndex current = index.get();
        if (current != null) {
            return current;
        }
        ModerationTermIndex loaded = ModerationTermIndex.of(termRepository.findAllActive());
        index.compareAndSet(null, loaded);
        return index.get();
    }

    private void record(ModeratedContentType contentType,
                        String fieldLabel,
                        ModerationVisibility visibility,
                        ModerationVerdict verdict,
                        UUID userId) {
        try {
            auditRepository.insertDecision(ModerationDecision.builder()
                    .id(UUID.randomUUID())
                    .contentType(contentType)
                    .contentId(null)
                    .fieldLabel(fieldLabel)
                    .userId(userId)
                    .visibility(visibility)
                    .layer(ModerationLayer.KEYWORD)
                    .decision(verdict.action())
                    .category(verdict.category())
                    .matchedTermId(verdict.matchedTermId())
                    .matchedText(truncate(verdict.matchedText()))
                    .policyVersion(config.getText(BusinessConfigKey.MODERATION_POLICY_VERSION))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException exception) {
            // Losing a metric row must never cost the user their submission.
            log.warn("Could not record moderation decision for {}: {}", contentType, exception.getMessage(), exception);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MATCHED_TEXT_LIMIT ? value : value.substring(0, MATCHED_TEXT_LIMIT);
    }
}

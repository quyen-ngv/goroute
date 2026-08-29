package com.ds.goroute.service.impl;

import com.ds.goroute.entity.ModerationDecision;
import com.ds.goroute.repository.ModerationAuditRepository;
import com.ds.goroute.service.AiTextModerationService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.thirdparty.ai.AiClient;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.ModerationFlagSource;
import com.ds.goroute.type.ModerationLayer;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTextModerationServiceImpl implements AiTextModerationService {

    private static final String SYSTEM_PROMPT = """
            You moderate user-generated travel content for a Vietnamese travel app.
            Judge the text in context: complaints, food names, place names and honest
            negative reviews are allowed. Only report a violation you would defend to
            the author.
            Answer with this JSON object and nothing else:
            {"violation": true|false, "category": one of \
            SEXUAL|VIOLENCE|HATE_SPEECH|HARASSMENT|POLITICAL|SPAM|SCAM|PERSONAL_DATA|COPYRIGHT, \
            "confidence": 0.0-1.0, "reason": "one short sentence"}
            """;

    /** Below this the model is guessing, and a guess is not worth a reviewer's time. */
    private static final double MINIMUM_CONFIDENCE = 0.6d;

    private final ObjectProvider<AiClient> aiClient;
    private final ContentModerationService contentModerationService;
    private final ModerationAuditRepository auditRepository;
    private final BusinessConfigService config;

    @Override
    @Async
    public void reviewLater(ModeratedContentType contentType,
                            UUID contentId,
                            UUID ownerId,
                            String fieldLabel,
                            String text) {
        if (!config.getBoolean(BusinessConfigKey.MODERATION_AI_TEXT_ENABLED)) {
            return;
        }
        AiClient client = aiClient.getIfAvailable();
        if (client == null || text == null || text.isBlank()) {
            return;
        }
        int maxLength = config.getInt(BusinessConfigKey.MODERATION_AI_TEXT_MAX_LENGTH);
        String payload = text.length() > maxLength ? text.substring(0, maxLength) : text;

        try {
            Optional<String> answer = client.completeJson(SYSTEM_PROMPT, payload);
            answer.flatMap(this::parse)
                    .ifPresent(verdict -> raise(contentType, contentId, ownerId, fieldLabel, verdict));
        } catch (RuntimeException exception) {
            // MOD-03 rule: a failing or slow external service is logged, never escalated
            // into a publishing outage.
            log.warn("AI moderation unavailable for {} {}: {}", contentType, contentId, exception.getMessage());
        }
    }

    private Optional<ModerationVerdict> parse(String json) {
        Map<String, Object> parsed = JsonUtils.fromJson(json, new TypeReference<>() {
        });
        if (parsed == null || !Boolean.TRUE.equals(parsed.get("violation"))) {
            return Optional.empty();
        }
        double confidence = parsed.get("confidence") instanceof Number number ? number.doubleValue() : 0d;
        if (confidence < MINIMUM_CONFIDENCE) {
            return Optional.empty();
        }
        ModerationCategory category = readCategory(parsed.get("category"));
        if (category == null) {
            return Optional.empty();
        }
        String reason = parsed.get("reason") == null ? null : String.valueOf(parsed.get("reason"));
        // The AI layer only ever flags. Blocking on a model's opinion, after the content
        // is already published, would remove content the author cannot argue about.
        return Optional.of(ModerationVerdict.of(ModerationAction.FLAG, category, null, reason));
    }

    private ModerationCategory readCategory(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return ModerationCategory.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void raise(ModeratedContentType contentType, UUID contentId, UUID ownerId,
                       String fieldLabel, ModerationVerdict verdict) {
        contentModerationService.flag(contentType, contentId, ownerId,
                ModerationFlagSource.AI_TEXT_FILTER, verdict, null);
        auditRepository.insertDecision(ModerationDecision.builder()
                .id(UUID.randomUUID())
                .contentType(contentType)
                .contentId(contentId)
                .fieldLabel(fieldLabel)
                .userId(ownerId)
                .visibility(ModerationVisibility.PUBLIC)
                .layer(ModerationLayer.AI_TEXT)
                .decision(verdict.action())
                .category(verdict.category())
                .matchedText(verdict.matchedText())
                .policyVersion(config.getText(BusinessConfigKey.MODERATION_POLICY_VERSION))
                .createdAt(LocalDateTime.now())
                .build());
    }
}

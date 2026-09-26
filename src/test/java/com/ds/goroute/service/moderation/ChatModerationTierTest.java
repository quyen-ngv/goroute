package com.ds.goroute.service.moderation;

import com.ds.goroute.entity.ModerationDecision;
import com.ds.goroute.entity.ModerationTerm;
import com.ds.goroute.repository.ModerationAuditRepository;
import com.ds.goroute.repository.ModerationTermRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.impl.TextModerationServiceImpl;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.ModerationStrictness;
import com.ds.goroute.type.ModerationVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The privacy half of the chat filter.
 *
 * <p>Chat is the one tier that refuses a message outright or stays out of the way: a flag
 * would file a copy of what somebody said into a queue a human reads, and no human is
 * allowed to read these threads. These tests are about what is <em>not</em> written down.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("The CHAT moderation tier")
class ChatModerationTierTest {

    private static final UUID AUTHOR = UUID.randomUUID();

    @Mock
    private ModerationTermRepository terms;
    @Mock
    private ModerationAuditRepository audit;
    @Mock
    private BusinessConfigService config;

    private TextModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TextModerationServiceImpl(terms, audit, config);
        when(config.getBoolean(BusinessConfigKey.MODERATION_TEXT_FILTER_ENABLED)).thenReturn(true);
        when(config.getText(BusinessConfigKey.MODERATION_POLICY_VERSION)).thenReturn("test");
        when(config.getEnum(BusinessConfigKey.MODERATION_STRICTNESS_CHAT, ModerationStrictness.class))
                .thenReturn(ModerationStrictness.KEYWORD_BLOCK_ONLY);
        when(terms.findAllActive()).thenReturn(List.of(
                term("cấmtừ", ModerationAction.BLOCK, ModerationCategory.HARASSMENT),
                term("hơikhó", ModerationAction.FLAG, ModerationCategory.HARASSMENT)));
    }

    @Test
    @DisplayName("refuses a blocked word on the spot")
    void blocks() {
        ModerationVerdict verdict = service.evaluate(
                ModeratedContentType.CHAT_MESSAGE, "content", ModerationVisibility.CHAT, "này cấmtừ nhé", AUTHOR);

        assertThat(verdict.action()).isEqualTo(ModerationAction.BLOCK);
    }

    @Test
    @DisplayName("turns a flag into nothing, so no copy of the message is queued for a human")
    void neverFlags() {
        ModerationVerdict verdict = service.evaluate(
                ModeratedContentType.CHAT_MESSAGE, "content", ModerationVisibility.CHAT, "câu này hơikhó đấy", AUTHOR);

        assertThat(verdict.action()).isEqualTo(ModerationAction.ALLOW);
        verify(audit, never()).insertDecision(any());
    }

    @Test
    @DisplayName("counts the term that fired but does not keep the sentence it fired on")
    void recordsTheTermWithoutTheText() {
        service.evaluate(ModeratedContentType.CHAT_MESSAGE, "content", ModerationVisibility.CHAT,
                "này cấmtừ nhé", AUTHOR);

        ArgumentCaptor<ModerationDecision> captor = ArgumentCaptor.forClass(ModerationDecision.class);
        verify(audit).insertDecision(captor.capture());
        ModerationDecision decision = captor.getValue();
        assertThat(decision.getDecision()).isEqualTo(ModerationAction.BLOCK);
        assertThat(decision.getMatchedTermId()).isNotNull();
        assertThat(decision.getMatchedText()).isNull();
    }

    @Test
    @DisplayName("still keeps the offending fragment for public content, where a human may look")
    void keepsTheTextForPublicContent() {
        when(config.getEnum(BusinessConfigKey.MODERATION_STRICTNESS_PUBLIC, ModerationStrictness.class))
                .thenReturn(ModerationStrictness.FULL);

        service.evaluate(ModeratedContentType.REVIEW, "text", ModerationVisibility.PUBLIC,
                "này cấmtừ nhé", AUTHOR);

        ArgumentCaptor<ModerationDecision> captor = ArgumentCaptor.forClass(ModerationDecision.class);
        verify(audit).insertDecision(captor.capture());
        assertThat(captor.getValue().getMatchedText()).isNotNull();
    }

    private ModerationTerm term(String value, ModerationAction action, ModerationCategory category) {
        return ModerationTerm.builder()
                .id(UUID.randomUUID())
                .term(value)
                .normalizedTerm(value)
                .category(category)
                .action(action)
                .isExemption(false)
                .isActive(true)
                .build();
    }
}

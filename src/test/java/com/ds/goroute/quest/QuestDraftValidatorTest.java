package com.ds.goroute.quest;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestQuestionChoice;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.service.QuestDraftValidator;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestDraftValidator")
class QuestDraftValidatorTest {

    @Mock private BusinessConfigService config;
    private QuestDraftValidator validator;

    @BeforeEach
    void setUp() {
        validator = new QuestDraftValidator(config);
        when(config.getInt(BusinessConfigKey.QUEST_PRICE_MAX_STARS)).thenReturn(200);
        when(config.getInt(BusinessConfigKey.QUEST_MIN_CHECKPOINTS)).thenReturn(2);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_CHECKPOINTS)).thenReturn(15);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_QUESTIONS_PER_CHECKPOINT)).thenReturn(3);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_BONUS_QUESTIONS)).thenReturn(2);
        when(config.getInt(BusinessConfigKey.QUEST_CHOICE_MIN_OPTIONS)).thenReturn(3);
        when(config.getInt(BusinessConfigKey.QUEST_CHOICE_MAX_OPTIONS)).thenReturn(5);
    }

    @Test
    @DisplayName("a well-formed two-checkpoint quest passes")
    void validQuestPasses() {
        QuestVersion version = version(
                checkpoint(true, textQuestion()),
                checkpoint(true, textQuestion()));
        assertThatCode(() -> validator.validateForSubmit(version)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("D21: a checkpoint with no required question and no required check-in is rejected")
    void checkpointWithNothingToDoIsRejected() {
        QuestCheckpoint empty = checkpoint(true);
        empty.setRequiresCheckin(false);
        QuestVersion version = version(checkpoint(true, textQuestion()), empty);

        assertThatThrownBy(() -> validator.validateForSubmit(version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required question or a required check-in");
    }

    @Test
    @DisplayName("D21: a checkpoint with only a required check-in and no question passes")
    void checkinOnlyCheckpointPasses() {
        QuestCheckpoint checkinOnly = checkpoint(true);
        checkinOnly.setRequiresCheckin(true);
        QuestVersion version = version(checkpoint(true, textQuestion()), checkinOnly);

        assertThatCode(() -> validator.validateForSubmit(version)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("§7.3: a checkpoint captured on a map, not in the field, blocks submission")
    void mapCapturedCheckpointBlocksSubmit() {
        QuestVersion version = version(checkpoint(true, textQuestion()), checkpoint(true, textQuestion()));
        version.getCheckpoints().get(1).setCaptureSource("MAP");

        assertThatThrownBy(() -> validator.validateForSubmit(version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("captured in the field");
    }

    @Test
    @DisplayName("a single-choice question with two correct options is rejected")
    void singleChoiceWithTwoCorrectRejected() {
        QuestQuestion choice = choiceQuestion(false, true, true);
        QuestVersion version = version(checkpoint(true, choice), checkpoint(true, textQuestion()));

        assertThatThrownBy(() -> validator.validateForSubmit(version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("more than one correct");
    }

    @Test
    @DisplayName("a choice question with too few options is rejected")
    void choiceWithTooFewOptionsRejected() {
        QuestQuestion choice = choiceQuestion(false, true);
        QuestVersion version = version(checkpoint(true, choice), checkpoint(true, textQuestion()));

        assertThatThrownBy(() -> validator.validateForSubmit(version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("options");
    }

    @Test
    @DisplayName("a number question with a non-numeric answer is rejected")
    void numberQuestionNonNumericRejected() {
        QuestQuestion number = QuestQuestion.builder()
                .id(UUID.randomUUID()).required(true).bonus(false).type("NUMBER")
                .prompt("How many pillars?").answerPlain("a lot").build();
        QuestVersion version = version(checkpoint(true, number), checkpoint(true, textQuestion()));

        assertThatThrownBy(() -> validator.validateForSubmit(version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not a number");
    }

    @Test
    @DisplayName("too few checkpoints is rejected")
    void tooFewCheckpointsRejected() {
        QuestVersion version = version(checkpoint(true, textQuestion()));
        assertThatThrownBy(() -> validator.validateForSubmit(version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("between 2 and 15 checkpoints");
    }

    @Test
    @DisplayName("a price above the ceiling is rejected")
    void priceAboveCeilingRejected() {
        QuestVersion version = version(checkpoint(true, textQuestion()), checkpoint(true, textQuestion()));
        version.setPriceStars(9999);
        assertThatThrownBy(() -> validator.validateForSubmit(version))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Price must be between");
    }

    // --- builders -------------------------------------------------------------------

    private static QuestVersion version(QuestCheckpoint... checkpoints) {
        return QuestVersion.builder()
                .id(UUID.randomUUID()).title("Old Quarter Trail").priceStars(0).rewardStars(20)
                .checkpoints(new ArrayList<>(List.of(checkpoints)))
                .build();
    }

    private static QuestCheckpoint checkpoint(boolean field, QuestQuestion... questions) {
        return QuestCheckpoint.builder()
                .id(UUID.randomUUID())
                .latitude(new BigDecimal("21.0288")).longitude(new BigDecimal("105.8524"))
                .captureSource(field ? "FIELD" : "MAP")
                .requiresCheckin(false)
                .questions(new ArrayList<>(List.of(questions)))
                .build();
    }

    private static QuestQuestion textQuestion() {
        return QuestQuestion.builder()
                .id(UUID.randomUUID()).required(true).bonus(false).type("TEXT")
                .prompt("What is carved above the gate?").answerPlain("Đông Kinh")
                .build();
    }

    private static QuestQuestion choiceQuestion(boolean... correctFlags) {
        List<QuestQuestionChoice> choices = new ArrayList<>();
        for (int i = 0; i < correctFlags.length; i++) {
            choices.add(QuestQuestionChoice.builder()
                    .id(UUID.randomUUID()).sortOrder(i).content("Option " + i).correct(correctFlags[i]).build());
        }
        return QuestQuestion.builder()
                .id(UUID.randomUUID()).required(true).bonus(false).type("CHOICE")
                .prompt("Which dynasty?").choices(choices).build();
    }
}

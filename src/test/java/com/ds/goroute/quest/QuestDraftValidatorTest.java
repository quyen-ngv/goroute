package com.ds.goroute.quest;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCheckpointClue;
import com.ds.goroute.quest.domain.QuestCheckpointStop;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestQuestionChoice;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.service.QuestDraftValidator;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        validator = new QuestDraftValidator(config, new MarketplaceJson(new ObjectMapper()));
        when(config.getInt(BusinessConfigKey.QUEST_PRICE_MAX_STARS)).thenReturn(200);
        when(config.getInt(BusinessConfigKey.QUEST_MIN_CHECKPOINTS)).thenReturn(2);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_CHECKPOINTS)).thenReturn(15);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_QUESTIONS_PER_CHECKPOINT)).thenReturn(3);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_BONUS_QUESTIONS)).thenReturn(2);
        when(config.getInt(BusinessConfigKey.QUEST_CHOICE_MIN_OPTIONS)).thenReturn(3);
        when(config.getInt(BusinessConfigKey.QUEST_CHOICE_MAX_OPTIONS)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.QUEST_UNLOCK_RADIUS_METERS)).thenReturn(40);
        when(config.getInt(BusinessConfigKey.QUEST_CLUE_MAX_STARS)).thenReturn(50);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_STOPS_PER_CHECKPOINT)).thenReturn(20);
        when(config.getInt(BusinessConfigKey.QUEST_STOP_MAX_DISTANCE_M)).thenReturn(1000);
        when(config.getInt(BusinessConfigKey.QUEST_AUDIO_MAX_SECONDS)).thenReturn(300);
    }

    // --- §3.14 dynamic checkpoints --------------------------------------------------

    @Test
    @DisplayName("§3.14 ARRIVE: a guide-only checkpoint with a story passes without any task")
    void arriveWithStoryPasses() {
        QuestCheckpoint guide = checkpoint(true);
        guide.setCompletionMode("ARRIVE");
        guide.setStory("Built in 1865 by the guild of silversmiths.");
        QuestVersion version = version(checkpoint(true, textQuestion()), guide);

        assertThatCode(() -> validator.validateForSubmit(version)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("§3.14 ARRIVE: nothing to take in, or a required task, is rejected")
    void arriveRules() {
        QuestCheckpoint empty = checkpoint(true);
        empty.setCompletionMode("ARRIVE");
        assertThatThrownBy(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), empty)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("needs a story, a recording or a storytelling point");

        QuestCheckpoint withTask = checkpoint(true, textQuestion());
        withTask.setCompletionMode("ARRIVE");
        withTask.setStory("A story");
        assertThatThrownBy(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), withTask)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("guide-only");
    }

    @Test
    @DisplayName("§3.15 AR_OBJECT: needs its object, an asset, a landmark when image-anchored, and no search area")
    void arObjectRules() {
        QuestCheckpoint missing = checkpoint(true);
        missing.setCompletionMode("AR_OBJECT");
        assertThatThrownBy(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), missing)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("needs its AR object");

        QuestCheckpoint cp = checkpoint(true);
        cp.setCompletionMode("AR_OBJECT");
        cp.setFindMode("AREA");
        cp.setSearchRadiusM(200);
        cp.setArObject("{\"behavior\":\"FIXED\",\"anchorMode\":\"IMAGE\",\"latitude\":21.0500,"
                + "\"longitude\":105.8524,\"spawnRadiusM\":150,\"markers\":[]}");
        assertThatThrownBy(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), cp)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be hidden in a search area")
                .hasMessageContaining("chosen from the library")
                .hasMessageContaining("more than 1000 m away")
                .hasMessageContaining("has not placed it at one");

        QuestCheckpoint good = checkpoint(true);
        good.setCompletionMode("AR_OBJECT");
        good.setArObject("{\"assetId\":\"" + UUID.randomUUID() + "\",\"behavior\":\"FIXED\","
                + "\"anchorMode\":\"APPROX\",\"latitude\":21.0289,\"longitude\":105.8524,\"spawnRadiusM\":150}");
        assertThatCode(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), good)))
                .as("an AR object is a task in itself: no question or check-in needed")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("§3.14 STOPS: min stops must be 1 to the number of points")
    void stopsMinRange() {
        QuestCheckpoint cp = checkpoint(true);
        cp.setCompletionMode("STOPS");
        cp.setMinStops(2);
        cp.setStops(List.of(stop("Bridge", "21.0290", "105.8525", 30)));

        assertThatThrownBy(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), cp)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must ask for 1 to 1 storytelling points");

        cp.setMinStops(1);
        assertThatCode(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), cp)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("§3.14 storytelling point: radius 10–100 m, within the distance limit, audio length capped")
    void stopRules() {
        QuestCheckpoint cp = checkpoint(true, textQuestion());
        QuestCheckpointStop far = stop("Far away", "21.0500", "105.8524", 30);
        QuestCheckpointStop wide = stop("Too wide", "21.0289", "105.8524", 150);
        QuestCheckpointStop longAudio = stop("Long", "21.0289", "105.8524", 30);
        longAudio.setAudioUrl("https://cdn.example/a.m4a");
        longAudio.setAudioSeconds(301);
        cp.setStops(List.of(far, wide, longAudio));

        assertThatThrownBy(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), cp)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("more than 1000 m from its checkpoint")
                .hasMessageContaining("radius of 10–100 m")
                .hasMessageContaining("longer than 300 seconds");
    }

    @Test
    @DisplayName("§3.14 AREA: search radius must exceed the unlock radius; REVEAL must be the last clue")
    void areaRules() {
        QuestCheckpoint cp = checkpoint(true, textQuestion());
        cp.setFindMode("AREA");
        cp.setRadiusM(60);
        cp.setSearchRadiusM(60);
        cp.setClues(List.of(
                QuestCheckpointClue.builder().tier(1).kind("REVEAL").costStars(0).build(),
                QuestCheckpointClue.builder().tier(2).kind("TEXT").text("Near the well").costStars(60).build()));

        assertThatThrownBy(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), cp)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("larger than its unlock radius")
                .hasMessageContaining("pin reveal must be the last clue")
                .hasMessageContaining("must cost 0 to 50 Stars");

        cp.setSearchRadiusM(200);
        cp.setClues(List.of(
                QuestCheckpointClue.builder().tier(1).kind("TEXT").text("Near the well").costStars(5).build(),
                QuestCheckpointClue.builder().tier(2).kind("REVEAL").costStars(20).build()));
        assertThatCode(() -> validator.validateForSubmit(version(checkpoint(true, textQuestion()), cp)))
                .doesNotThrowAnyException();
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
    @DisplayName("§7.3 (2026-09-27): a checkpoint pinned on a map may be submitted without an on-site capture")
    void mapCapturedCheckpointMaySubmit() {
        QuestVersion version = version(checkpoint(true, textQuestion()), checkpoint(false, textQuestion()));

        assertThatCode(() -> validator.validateForSubmit(version)).doesNotThrowAnyException();
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

    private static QuestCheckpointStop stop(String name, String lat, String lng, int radius) {
        return QuestCheckpointStop.builder()
                .id(UUID.randomUUID()).name(name).latitude(new BigDecimal(lat)).longitude(new BigDecimal(lng))
                .radiusM(radius).build();
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

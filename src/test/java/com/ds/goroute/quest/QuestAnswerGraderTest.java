package com.ds.goroute.quest;

import com.ds.goroute.quest.domain.QuestQuestionChoice;
import com.ds.goroute.quest.service.QuestAnswerGrader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuestAnswerGrader")
class QuestAnswerGraderTest {

    private final QuestAnswerGrader grader = new QuestAnswerGrader();

    @Test
    @DisplayName("Vietnamese diacritics and đ are stripped, case and spacing ignored")
    void normalisation() {
        assertThat(QuestAnswerGrader.normalize("Chùa Một Cột")).isEqualTo("chua mot cot");
        assertThat(QuestAnswerGrader.normalize("  ĐÌNH  làng ")).isEqualTo("dinh lang");
        assertThat(QuestAnswerGrader.normalize("Hà Nội!")).isEqualTo("ha noi");
    }

    @Test
    @DisplayName("text answers match with or without diacritics")
    void textMatchesWithoutDiacritics() {
        assertThat(grader.gradeText("chùa", List.of(), "chua")).isTrue();
        assertThat(grader.gradeText("chùa", List.of(), "CHÙA")).isTrue();
        assertThat(grader.gradeText("chùa", List.of(), "đền")).isFalse();
    }

    @Test
    @DisplayName("accepted variants match")
    void textMatchesVariants() {
        assertThat(grader.gradeText("Văn Miếu", List.of("Quốc Tử Giám"), "quoc tu giam")).isTrue();
        assertThat(grader.gradeText("Văn Miếu", List.of("Quốc Tử Giám"), "khong biet")).isFalse();
    }

    @Test
    @DisplayName("an empty guess is never correct")
    void emptyGuessWrong() {
        assertThat(grader.gradeText("chùa", List.of(), "")).isFalse();
        assertThat(grader.gradeText("chùa", List.of(), "   ")).isFalse();
    }

    @Test
    @DisplayName("numbers match exactly, or within tolerance")
    void numberGrading() {
        assertThat(grader.gradeNumber("18", null, "18")).isTrue();
        assertThat(grader.gradeNumber("18", null, "19")).isFalse();
        assertThat(grader.gradeNumber("100", new BigDecimal("5"), "103")).isTrue();
        assertThat(grader.gradeNumber("100", new BigDecimal("5"), "106")).isFalse();
        assertThat(grader.gradeNumber("1000", null, "1,000")).isTrue();
        assertThat(grader.gradeNumber("18", null, "eighteen")).isFalse();
    }

    @Test
    @DisplayName("single choice matches by id, and rejects extra selections")
    void singleChoice() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<QuestQuestionChoice> choices = List.of(
                choice(a, true), choice(b, false), choice(c, false));
        assertThat(grader.gradeChoice(choices, List.of(a), false)).isTrue();
        assertThat(grader.gradeChoice(choices, List.of(b), false)).isFalse();
        assertThat(grader.gradeChoice(choices, List.of(a, b), false)).isFalse();
    }

    @Test
    @DisplayName("multi choice requires the exact correct set")
    void multiChoice() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<QuestQuestionChoice> choices = List.of(
                choice(a, true), choice(b, true), choice(c, false));
        assertThat(grader.gradeChoice(choices, List.of(a, b), true)).isTrue();
        assertThat(grader.gradeChoice(choices, List.of(b, a), true)).isTrue();
        assertThat(grader.gradeChoice(choices, List.of(a), true)).isFalse();
        assertThat(grader.gradeChoice(choices, List.of(a, b, c), true)).isFalse();
    }

    @Test
    @DisplayName("photo always passes")
    void photoAlwaysCorrect() {
        assertThat(grader.gradePhoto()).isTrue();
    }

    private static QuestQuestionChoice choice(UUID id, boolean correct) {
        return QuestQuestionChoice.builder().id(id).correct(correct).build();
    }
}

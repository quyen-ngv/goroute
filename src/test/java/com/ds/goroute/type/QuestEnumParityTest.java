package com.ds.goroute.type;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The backend quest enums against the console's mirror, closing the backend↔console side of the
 * three-way parity (the app↔console side lives in the app's {@code quest_enums_parity_test.dart}).
 *
 * <p>Reads {@code questEnums.ts} rather than repeating the lists here, so a value added on one
 * side and not the other is a red test. Skips when the console is not checked out beside the
 * backend, so the backend suite still runs on its own.
 */
@DisplayName("Quest enum parity (backend ↔ console)")
class QuestEnumParityTest {

    private static final Path CONSOLE_ENUMS =
            Path.of("..", "goroute-admin", "src", "domain", "questEnums.ts");

    private static String readConsole() {
        Assumptions.assumeTrue(Files.exists(CONSOLE_ENUMS),
                "goroute-admin not checked out beside goroute");
        try {
            return Files.readString(CONSOLE_ENUMS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The string values of one {@code export const NAME = ['A', 'B'] as const;}. */
    private static List<String> tsList(String source, String name) {
        Matcher block = Pattern.compile("export const " + name + "\\s*=\\s*\\[([^\\]]*)]")
                .matcher(source);
        assertThat(block.find()).as("%s is declared in questEnums.ts", name).isTrue();
        List<String> values = new ArrayList<>();
        Matcher value = Pattern.compile("'([^']*)'").matcher(block.group(1));
        while (value.find()) {
            values.add(value.group(1));
        }
        return values;
    }

    private static List<String> names(Enum<?>[] values) {
        return Stream.of(values).map(Enum::name).toList();
    }

    @Test
    @DisplayName("QUEST_STATUSES matches QuestStatus, value for value and in order")
    void questStatuses() {
        assertThat(tsList(readConsole(), "QUEST_STATUSES")).isEqualTo(names(QuestStatus.values()));
    }

    @Test
    @DisplayName("QUEST_ORIGINS matches QuestOrigin")
    void questOrigins() {
        assertThat(tsList(readConsole(), "QUEST_ORIGINS")).isEqualTo(names(QuestOrigin.values()));
    }

    @Test
    @DisplayName("QUEST_CREATOR_STATUSES matches QuestCreatorStatus")
    void questCreatorStatuses() {
        assertThat(tsList(readConsole(), "QUEST_CREATOR_STATUSES")).isEqualTo(names(QuestCreatorStatus.values()));
    }

    @Test
    @DisplayName("QUEST_QUESTION_TYPES matches QuestQuestionType")
    void questQuestionTypes() {
        assertThat(tsList(readConsole(), "QUEST_QUESTION_TYPES")).isEqualTo(names(QuestQuestionType.values()));
    }

    @Test
    @DisplayName("QUEST_CAPTURE_SOURCES matches QuestCaptureSource")
    void questCaptureSources() {
        assertThat(tsList(readConsole(), "QUEST_CAPTURE_SOURCES")).isEqualTo(names(QuestCaptureSource.values()));
    }

    @Test
    @DisplayName("QUEST_VERSION_CHANGE_KINDS matches QuestVersionChangeKind")
    void questVersionChangeKinds() {
        assertThat(tsList(readConsole(), "QUEST_VERSION_CHANGE_KINDS"))
                .isEqualTo(names(QuestVersionChangeKind.values()));
    }

    @Test
    @DisplayName("QUEST_RUN_STATUSES matches QuestRunStatus")
    void questRunStatuses() {
        assertThat(tsList(readConsole(), "QUEST_RUN_STATUSES")).isEqualTo(names(QuestRunStatus.values()));
    }

    @Test
    @DisplayName("QUEST_REVIEW_DECISIONS matches QuestReviewDecision")
    void questReviewDecisions() {
        assertThat(tsList(readConsole(), "QUEST_REVIEW_DECISIONS")).isEqualTo(names(QuestReviewDecision.values()));
    }
}

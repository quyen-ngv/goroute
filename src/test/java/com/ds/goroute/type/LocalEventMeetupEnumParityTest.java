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
 * The local-event and meetup enums against the console's mirrors ({@code eventEnums.ts},
 * {@code meetupEnums.ts}), the way {@link QuestEnumParityTest} pins the quest ones: a value added
 * on one side only is a red test. Skips when the console is not checked out beside the backend.
 */
@DisplayName("Local event + meetup enum parity (backend ↔ console)")
class LocalEventMeetupEnumParityTest {

    private static final Path CONSOLE_DOMAIN = Path.of("..", "goroute-admin", "src", "domain");

    private static String read(String fileName) {
        Path path = CONSOLE_DOMAIN.resolve(fileName);
        Assumptions.assumeTrue(Files.exists(path), "goroute-admin not checked out beside goroute");
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The quoted values of {@code export const NAME = [...]}; a {@code ...OTHER} spread is skipped. */
    private static List<String> tsList(String source, String name) {
        Matcher block = Pattern.compile("export const " + name + "\\s*=\\s*\\[([^\\]]*)]").matcher(source);
        assertThat(block.find()).as("%s is declared", name).isTrue();
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
    @DisplayName("every local-event enum matches eventEnums.ts, value for value and in order")
    void localEventEnums() {
        String console = read("eventEnums.ts");
        assertThat(tsList(console, "LOCAL_EVENT_CATEGORIES")).isEqualTo(names(LocalEventCategory.values()));
        assertThat(tsList(console, "LOCAL_EVENT_STATUSES")).isEqualTo(names(LocalEventStatus.values()));
        assertThat(tsList(console, "LOCAL_EVENT_PUBLISH_STATUSES")).isEqualTo(names(LocalEventPublishStatus.values()));
        assertThat(tsList(console, "LOCAL_EVENT_ATTENDANCE_MODES")).isEqualTo(names(LocalEventAttendanceMode.values()));
        assertThat(tsList(console, "LOCAL_EVENT_TICKET_AVAILABILITIES"))
                .isEqualTo(names(LocalEventTicketAvailability.values()));
        assertThat(tsList(console, "LOCAL_EVENT_IMPORT_RESULTS")).isEqualTo(names(LocalEventImportResult.values()));
        assertThat(tsList(console, "LOCAL_EVENT_IMAGE_STATUSES")).isEqualTo(names(LocalEventImageStatus.values()));
        assertThat(tsList(console, "ADMIN_API_KEY_SCOPES"))
                .isEqualTo(Stream.of(AdminApiKeyScope.values()).map(AdminApiKeyScope::code).toList());
    }

    @Test
    @DisplayName("the console labels every import field-error code")
    void importErrorLabels() {
        String console = read("eventEnums.ts");
        Matcher block = Pattern.compile("LOCAL_EVENT_IMPORT_ERROR_LABELS[^{]*\\{([^}]*)}").matcher(console);
        assertThat(block.find()).isTrue();
        for (LocalEventFieldErrorCode code : LocalEventFieldErrorCode.values()) {
            assertThat(block.group(1)).as("label for %s", code).contains(code.name() + ":");
        }
    }

    @Test
    @DisplayName("every meetup enum matches meetupEnums.ts, value for value and in order")
    void meetupEnums() {
        String console = read("meetupEnums.ts");
        assertThat(tsList(console, "MEETUP_CATEGORIES")).isEqualTo(names(MeetupCategory.values()));
        assertThat(tsList(console, "MEETUP_STATUSES")).isEqualTo(names(MeetupStatus.values()));
        List<String> effective = new ArrayList<>(tsList(console, "MEETUP_STATUSES"));
        effective.addAll(tsList(console, "MEETUP_EFFECTIVE_STATUSES"));
        assertThat(effective).isEqualTo(names(MeetupEffectiveStatus.values()));
        assertThat(tsList(console, "MEETUP_LOCATION_MODES")).isEqualTo(names(MeetupLocationMode.values()));
        assertThat(tsList(console, "MEETUP_TIME_MODES")).isEqualTo(names(MeetupTimeMode.values()));
        assertThat(tsList(console, "MEETUP_JOIN_POLICIES")).isEqualTo(names(MeetupJoinPolicy.values()));
        assertThat(tsList(console, "MEETUP_PARTICIPANT_STATUSES")).isEqualTo(names(MeetupParticipantStatus.values()));
        assertThat(tsList(console, "MEETUP_REJECT_REASONS")).isEqualTo(names(MeetupRejectReason.values()));
    }

    @Test
    @DisplayName("the console's sort values are the ones MeetupAdminSort.fromWire reads")
    void meetupAdminSorts() {
        List<String> sorts = tsList(read("meetupEnums.ts"), "MEETUP_ADMIN_SORTS");
        assertThat(sorts.stream().map(MeetupAdminSort::fromWire).toList())
                .containsExactly(MeetupAdminSort.values());
    }
}

package com.ds.goroute.dto;

import com.ds.goroute.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedCursorTest {

    /** A feed row reduced to its sort key. */
    private record Row(boolean hasPhoto, LocalDateTime createdAt, UUID id) {
    }

    /**
     * ORDER BY has_photo DESC, created_at DESC, id DESC, as PostgreSQL evaluates it. uuid
     * compares as unsigned bytes there, which is the order of its lowercase hex text, not
     * Java's signed UUID.compareTo.
     */
    private static final Comparator<Row> FEED_ORDER = Comparator
            .comparing(Row::hasPhoto)
            .thenComparing(Row::createdAt)
            .thenComparing(row -> row.id().toString())
            .reversed();

    /** {@code (has_photo, created_at, id) < (cursor)}, the predicate the mappers bind. */
    private static boolean isAfter(Row row, FeedCursor cursor) {
        Row key = new Row(cursor.hasPhoto(), cursor.createdAt(), cursor.id());
        return FEED_ORDER.compare(row, key) > 0;
    }

    private static List<Row> page(List<Row> table, String cursor, int size) {
        FeedCursor after = FeedCursor.decode(cursor);
        return table.stream()
                .sorted(FEED_ORDER)
                .filter(row -> after == null || isAfter(row, after))
                .limit(size)
                .toList();
    }

    private static String cursorOf(Row row) {
        return FeedCursor.encodeAfter(row.hasPhoto(), row.createdAt(), row.id());
    }

    @Test
    void encodesAndDecodesTheExactSortKey() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 7, 9, 15, 2, 123_456_000);

        FeedCursor decoded = FeedCursor.decode(FeedCursor.encodeAfter(true, createdAt, id));

        assertThat(decoded).isEqualTo(new FeedCursor(true, createdAt, id));
    }

    @Test
    void keepsWholeSecondsAndMinutesThatToStringWouldShorten() {
        // LocalDateTime.toString() prints 10:00 for a whole minute; the cursor must not depend on it.
        LocalDateTime wholeMinute = LocalDateTime.of(2026, 10, 7, 10, 0);
        UUID id = UUID.randomUUID();

        assertThat(FeedCursor.decode(FeedCursor.encodeAfter(false, wholeMinute, id)).createdAt())
                .isEqualTo(wholeMinute);
    }

    @Test
    void isUrlSafeAndOpaque() {
        String encoded = FeedCursor.encodeAfter(true, LocalDateTime.now(), UUID.randomUUID());

        assertThat(encoded).doesNotContain("+", "/", "=", "|");
    }

    @Test
    void noCursorMeansNoKeyset() {
        assertThat(FeedCursor.decode(null)).isNull();
        assertThat(FeedCursor.decode("  ")).isNull();
        assertThat(FeedCursor.encodeAfter(true, null, UUID.randomUUID())).isNull();
        assertThat(FeedCursor.encodeAfter(true, LocalDateTime.now(), null)).isNull();
    }

    @Test
    void aNullPhotoFlagIsAPostWithoutPhotos() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        assertThat(FeedCursor.decode(FeedCursor.encodeAfter(null, createdAt, id)).hasPhoto()).isFalse();
    }

    @Test
    void rejectsAnythingTheServerDidNotIssue() {
        String notBase64 = "%%%";
        String wrongShape = java.util.Base64.getUrlEncoder().encodeToString("1|2026-10-07T10:00".getBytes());
        String badFlag = java.util.Base64.getUrlEncoder()
                .encodeToString(("x|2026-10-07T10:00:00|" + UUID.randomUUID()).getBytes());
        String badUuid = java.util.Base64.getUrlEncoder().encodeToString("1|2026-10-07T10:00:00|nope".getBytes());
        String tooLong = "A".repeat(500);

        for (String cursor : List.of(notBase64, wrongShape, badFlag, badUuid, tooLong)) {
            assertThatThrownBy(() -> FeedCursor.decode(cursor))
                    .as(cursor)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid feed cursor");
        }
    }

    @Test
    void thePageAfterTheLastPhotoPostContinuesWithPostsWithoutPhotos() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 12, 0);
        Row oldWithPhoto = new Row(true, now.minusDays(30), UUID.randomUUID());
        Row newWithoutPhoto = new Row(false, now, UUID.randomUUID());

        List<Row> table = List.of(newWithoutPhoto, oldWithPhoto);
        List<Row> first = page(table, null, 1);
        List<Row> second = page(table, cursorOf(first.getLast()), 1);

        assertThat(first).containsExactly(oldWithPhoto);
        assertThat(second).as("false sorts after true under DESC, so the row comparison is <")
                .containsExactly(newWithoutPhoto);
    }

    @Test
    void rowsSharingATimestampAreSplitByIdWithoutLossOrRepeat() {
        LocalDateTime sameMoment = LocalDateTime.of(2026, 10, 7, 12, 0);
        List<Row> table = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            table.add(new Row(index % 2 == 0, sameMoment, UUID.randomUUID()));
        }

        List<Row> seen = readAll(table, 2);

        assertThat(seen).containsExactlyElementsOf(table.stream().sorted(FEED_ORDER).toList());
    }

    @Test
    void aPostPublishedBetweenTwoPagesNeitherRepeatsNorSkipsARow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 12, 0);
        List<Row> table = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            table.add(new Row(index % 3 != 0, now.minusMinutes(index), UUID.randomUUID()));
        }
        List<Row> before = table.stream().sorted(FEED_ORDER).toList();

        List<Row> first = page(table, null, 4);
        // A new post with a photo lands at the very top while the reader is on page one.
        table.add(new Row(true, now.plusMinutes(1), UUID.randomUUID()));
        List<Row> rest = new ArrayList<>();
        String cursor = cursorOf(first.getLast());
        while (true) {
            List<Row> next = page(table, cursor, 4);
            if (next.isEmpty()) {
                break;
            }
            rest.addAll(next);
            cursor = cursorOf(next.getLast());
        }

        List<Row> seen = new ArrayList<>(first);
        seen.addAll(rest);
        Set<UUID> distinct = new HashSet<>();
        seen.forEach(row -> distinct.add(row.id()));
        assertThat(distinct).as("no repeats").hasSize(seen.size());
        assertThat(seen).as("every row that existed when scrolling began, in feed order")
                .containsExactlyElementsOf(before);
    }

    private static List<Row> readAll(List<Row> table, int size) {
        List<Row> seen = new ArrayList<>();
        String cursor = null;
        while (true) {
            List<Row> next = page(table, cursor, size);
            if (next.isEmpty()) {
                return seen;
            }
            seen.addAll(next);
            cursor = cursorOf(next.getLast());
        }
    }
}

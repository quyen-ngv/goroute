package com.ds.goroute.dto;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Where a page of the public check-in or review feed ended: the sort key of its last post.
 *
 * <p>Both feeds order by {@code has_photo DESC, created_at DESC, id DESC}, so the next page is
 * every row whose key is smaller, {@code (has_photo, created_at, id) < (hasPhoto, createdAt, id)}.
 * Every column sorts descending, which is why one row comparison is the whole predicate: a
 * post without photos sorts after every post with one, and {@code false < true}. A post that
 * arrives while someone scrolls has a larger key, so it can neither repeat a row nor push one
 * off the page boundary the way offset paging did.
 *
 * <p>On the wire the cursor is opaque: URL-safe Base64 of {@code 1|2026-10-07T09:15:02.123456|uuid}.
 * Clients send back what the server gave them and never build one.
 */
public record FeedCursor(boolean hasPhoto, LocalDateTime createdAt, UUID id) {

    private static final String SEPARATOR = "|";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    /** Far longer than any real cursor; a guard against decoding an arbitrary large header. */
    private static final int MAX_ENCODED_LENGTH = 200;

    public FeedCursor {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(id, "id");
    }

    /** The cursor that resumes after this post, or null when the row lacks a sort key. */
    public static String encodeAfter(Boolean hasPhoto, LocalDateTime createdAt, UUID id) {
        if (createdAt == null || id == null) {
            return null;
        }
        return new FeedCursor(Boolean.TRUE.equals(hasPhoto), createdAt, id).encode();
    }

    public String encode() {
        String raw = (hasPhoto ? "1" : "0") + SEPARATOR + TIMESTAMP.format(createdAt) + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The cursor a client sent back; null when it sent none. Anything that is not a cursor this
     * server issued is a 400 rather than a silent restart from the top, which would show the
     * first page again as if it were the next.
     */
    public static FeedCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        if (encoded.length() > MAX_ENCODED_LENGTH) {
            throw invalid();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + SEPARATOR, -1);
            if (parts.length != 3 || !("1".equals(parts[0]) || "0".equals(parts[0]))) {
                throw invalid();
            }
            return new FeedCursor("1".equals(parts[0]),
                    LocalDateTime.parse(parts[1], TIMESTAMP),
                    UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid feed cursor");
    }
}

package com.ds.goroute.localevent.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Text clean-up rules shared by the normaliser and the console search. */
public final class LocalEventText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** Control characters except tab and newline. */
    private static final Pattern CONTROL_KEEP_LINES = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern CONTROL_ALL = Pattern.compile("\\p{Cc}");
    private static final int TITLE_NORM_MAX = 200;

    private LocalEventText() {
    }

    /**
     * Lower case, diacritics stripped, đ→d, single spaces. Feeds {@code title_norm} and the
     * pg_trgm duplicate check ("Lễ hội Đền Hùng" and "le hoi den hung" compare equal).
     */
    public static String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        String lower = title.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String stripped = MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        String single = SPACES.matcher(stripped).replaceAll(" ").trim();
        return single.length() > TITLE_NORM_MAX ? single.substring(0, TITLE_NORM_MAX) : single;
    }

    /** One-line text: control characters removed, trimmed; blank becomes null. */
    public static String singleLine(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = CONTROL_ALL.matcher(value).replaceAll(" ").trim();
        cleaned = SPACES.matcher(cleaned).replaceAll(" ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Multi-line text (description): CRLF to LF, other control characters removed, trimmed. */
    public static String multiLine(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = CONTROL_KEEP_LINES.matcher(value.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Escapes LIKE wildcards for a pattern used with {@code ESCAPE '\'}. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

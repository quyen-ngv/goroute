package com.ds.goroute.utils;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Turns arbitrary user text into the single canonical form the term list is matched
 * against (MOD-02).
 *
 * <p>Vietnamese evasion is the reason this class exists. The same word is written with
 * and without diacritics, with dots or dashes wedged between letters, with digits
 * standing in for look-alike letters, and in mixed case. Matching raw text would catch
 * none of those, and keeping five different normalizations around would guarantee that
 * the term list and the incoming text eventually disagree.
 *
 * <p>Two forms are produced, and both are needed:
 * <ul>
 *   <li>{@link #normalize(String)} keeps word boundaries so a short term such as
 *       {@code dcm} can be matched as a whole word instead of inside {@code dcmxyz}.</li>
 *   <li>{@link #compact(String)} removes every separator so that spacing out a word
 *       ({@code d c m}) does not defeat the filter.</li>
 * </ul>
 */
public final class ModerationTextNormalizer {

    /** Look-alike substitutions seen in real evasion attempts. */
    private static final char[][] LOOKALIKES = {
            {'0', 'o'}, {'1', 'i'}, {'3', 'e'}, {'4', 'a'}, {'5', 's'},
            {'6', 'g'}, {'7', 't'}, {'8', 'b'}, {'9', 'g'},
            {'@', 'a'}, {'$', 's'}, {'|', 'i'}, {'!', 'i'}, {'+', 't'},
    };

    private static final int MAX_REPEATED_RUN = 2;

    private ModerationTextNormalizer() {
    }

    /**
     * Canonical form with single spaces preserved between words: lower-cased, diacritics
     * stripped, look-alike characters folded, punctuation reduced to a space and long
     * character runs collapsed.
     */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder builder = new StringBuilder(decomposed.length());
        int repeated = 0;
        char previous = 0;
        for (int index = 0; index < decomposed.length(); index++) {
            char current = fold(decomposed.charAt(index));
            if (current == 0) {
                continue;
            }
            if (current == ' ') {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ' ') {
                    builder.append(' ');
                }
                previous = 0;
                repeated = 0;
                continue;
            }
            // "loooove" and "looove" must reach the same form as "loove".
            repeated = current == previous ? repeated + 1 : 0;
            previous = current;
            if (repeated < MAX_REPEATED_RUN) {
                builder.append(current);
            }
        }
        return builder.toString().trim();
    }

    /** {@link #normalize(String)} with every space removed, to defeat spaced-out words. */
    public static String compact(String text) {
        return normalize(text).replace(" ", "");
    }

    /**
     * Joins runs of isolated single characters back into one word, so {@code d c m} reads
     * as {@code dcm} while the rest of the sentence keeps its boundaries.
     *
     * <p>This is the targeted answer to letter-spacing. Stripping every space instead
     * would also fuse ordinary words together and make short terms fire inside unrelated
     * ones -- the kind of false block that costs a user.
     *
     * @param normalized output of {@link #normalize(String)}
     */
    public static String joinSpacedLetters(String normalized) {
        if (normalized.isEmpty()) {
            return normalized;
        }
        String[] tokens = normalized.split(" ");
        StringBuilder result = new StringBuilder(normalized.length());
        StringBuilder run = new StringBuilder();
        for (String token : tokens) {
            if (token.length() == 1) {
                run.append(token);
                continue;
            }
            appendRun(result, run);
            append(result, token);
        }
        appendRun(result, run);
        return result.toString();
    }

    private static void appendRun(StringBuilder result, StringBuilder run) {
        if (run.length() > 0) {
            append(result, run.toString());
            run.setLength(0);
        }
    }

    private static void append(StringBuilder result, String token) {
        if (result.length() > 0) {
            result.append(' ');
        }
        result.append(token);
    }

    /**
     * True when the match at {@code start} covers whole words. Short terms are only
     * meaningful with boundaries: without this, {@code cho} would fire inside
     * {@code cho hoi} and every other Vietnamese sentence.
     */
    public static boolean isWholeWordMatch(String haystack, int start, int length) {
        int end = start + length;
        boolean leftClear = start == 0 || !isWordCharacter(haystack.charAt(start - 1));
        boolean rightClear = end >= haystack.length() || !isWordCharacter(haystack.charAt(end));
        return leftClear && rightClear;
    }

    private static boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value);
    }

    /**
     * Folds one decomposed character. Returns {@code 0} for characters that carry no
     * meaning for matching (combining marks), and {@code ' '} for separators.
     */
    private static char fold(char value) {
        if (Character.getType(value) == Character.NON_SPACING_MARK) {
            return 0;
        }
        if (value == 'd' || value == 'đ') {
            // NFD leaves Vietnamese "d with stroke" as a single character.
            return 'd';
        }
        for (char[] pair : LOOKALIKES) {
            if (value == pair[0]) {
                return pair[1];
            }
        }
        if (Character.isLetterOrDigit(value)) {
            return value;
        }
        return ' ';
    }
}

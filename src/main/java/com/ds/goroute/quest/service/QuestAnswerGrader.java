package com.ds.goroute.quest.service;

import com.ds.goroute.quest.domain.QuestQuestionChoice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Grades a player's answer on the server (D6) — the client only learns right or wrong.
 *
 * <p>Text grading is the part most likely to ruin the experience (§3.12): a great many players
 * type without Vietnamese diacritics, so "chùa" and "chua" must match. Both the accepted answers
 * and the guess are normalised the same way before comparison, and the accent-stripped variants
 * are generated here rather than asked of the creator.
 */
@Component
public class QuestAnswerGrader {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^\\p{Punct}+|\\p{Punct}+$");

    /**
     * Lowercase, strip Vietnamese diacritics (including đ→d, which NFD does not decompose),
     * collapse whitespace, and trim edge punctuation. Idempotent.
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.forLanguageTag("vi"));
        String noDiacritics = COMBINING_MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        String noDstroke = noDiacritics.replace('đ', 'd').replace('Đ', 'd');
        String collapsed = WHITESPACE.matcher(noDstroke).replaceAll(" ").trim();
        return EDGE_PUNCTUATION.matcher(collapsed).replaceAll("").trim();
    }

    /** True when the guess matches the plain answer or any accepted variant, after normalisation. */
    public boolean gradeText(String answerPlain, List<String> variants, String guess) {
        String normalizedGuess = normalize(guess);
        if (normalizedGuess.isEmpty()) {
            return false;
        }
        Set<String> accepted = acceptedAnswers(answerPlain, variants).map(QuestAnswerGrader::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return accepted.contains(normalizedGuess);
    }

    /** True when the guessed number equals the answer within the optional tolerance (default 0). */
    public boolean gradeNumber(String answerPlain, BigDecimal tolerance, String guess) {
        Double answer = parse(answerPlain);
        Double value = parse(guess);
        if (answer == null || value == null) {
            return false;
        }
        double slack = tolerance == null ? 0d : Math.abs(tolerance.doubleValue());
        return Math.abs(answer - value) <= slack;
    }

    /**
     * True when the guessed choice ids are exactly the correct ones. For {@code CHOICE} there is one
     * correct option; for {@code MULTI_CHOICE} the whole set must match. Comparison is by stable id,
     * never by index, because the order is shuffled each run (§3.12).
     */
    public boolean gradeChoice(List<QuestQuestionChoice> choices, List<UUID> guessIds, boolean multi) {
        Set<UUID> correct = choices.stream()
                .filter(QuestQuestionChoice::isCorrect)
                .map(QuestQuestionChoice::getId)
                .collect(Collectors.toSet());
        Set<UUID> guess = guessIds == null ? Set.of() : Set.copyOf(guessIds);
        if (correct.isEmpty()) {
            return false;
        }
        if (!multi && (correct.size() != 1 || guess.size() != 1)) {
            return false;
        }
        return correct.equals(guess);
    }

    /** PHOTO always scores correct (D12); the camera guard is enforced where the photo is taken. */
    public boolean gradePhoto() {
        return true;
    }

    private static java.util.stream.Stream<String> acceptedAnswers(String head, List<String> tail) {
        java.util.stream.Stream<String> variants = tail == null ? java.util.stream.Stream.empty() : tail.stream();
        return java.util.stream.Stream.concat(java.util.stream.Stream.ofNullable(head), variants);
    }

    private static Double parse(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replace(",", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

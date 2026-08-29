package com.ds.goroute.service.moderation;

import com.ds.goroute.entity.ModerationTerm;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.utils.ModerationTextNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * An immutable, pre-normalized snapshot of the term list, and the matcher that runs
 * against it.
 *
 * <p>Two rules are encoded here and nowhere else, so they cannot drift apart:
 *
 * <ol>
 *   <li><b>Exemptions win, and only where they apply.</b> Matched exemption spans are
 *       masked out of the text before restricted terms are scanned, so
 *       {@code tuong khoa than} in a museum review is exempt while
 *       {@code khoa than} elsewhere in the same review still fires.</li>
 *   <li><b>Single-word terms need word boundaries.</b> Substring matching on short
 *       Vietnamese tokens produces false blocks on ordinary sentences, and a false block
 *       costs a user.</li>
 * </ol>
 *
 * <p>Matching is a linear scan per term. With a few hundred terms and text capped at a
 * few thousand characters that is well under a millisecond; the day the list reaches
 * thousands of entries, replace the scan with an Aho-Corasick automaton behind this same
 * method signature.
 */
public final class ModerationTermIndex {

    /**
     * Written over exempt spans. A control character is used so it can never collide
     * with normalized text, which only ever contains letters, digits and spaces.
     */
    private static final char MASK = (char) 1;

    private final List<CompiledTerm> exemptions;
    private final List<CompiledTerm> restricted;

    private ModerationTermIndex(List<CompiledTerm> exemptions, List<CompiledTerm> restricted) {
        this.exemptions = exemptions;
        this.restricted = restricted;
    }

    public static ModerationTermIndex of(Collection<ModerationTerm> terms) {
        List<CompiledTerm> exemptions = new ArrayList<>();
        List<CompiledTerm> restricted = new ArrayList<>();
        for (ModerationTerm term : terms) {
            CompiledTerm compiled = CompiledTerm.from(term);
            if (compiled == null) {
                continue;
            }
            (Boolean.TRUE.equals(term.getIsExemption()) ? exemptions : restricted).add(compiled);
        }
        // Longest first: a longer phrase is the more specific statement about the text,
        // both for exempting and for classifying.
        exemptions.sort(Comparator.comparingInt((CompiledTerm term) -> term.pattern.length()).reversed());
        restricted.sort(Comparator.comparingInt((CompiledTerm term) -> term.pattern.length()).reversed());
        return new ModerationTermIndex(List.copyOf(exemptions), List.copyOf(restricted));
    }

    public boolean isEmpty() {
        return restricted.isEmpty();
    }

    public int size() {
        return restricted.size() + exemptions.size();
    }

    /**
     * Runs the list over {@code text} and returns the strongest verdict, or
     * {@link ModerationVerdict#allowed()} when nothing matched.
     */
    public ModerationVerdict evaluate(String text) {
        String spaced = ModerationTextNormalizer.normalize(text);
        if (spaced.isEmpty()) {
            return ModerationVerdict.allowed();
        }

        StringBuilder masked = new StringBuilder(spaced);
        for (CompiledTerm exemption : exemptions) {
            maskAll(masked, exemption);
        }
        String searchable = masked.toString();
        // Both evasion variants are derived after masking, so an exempt phrase can never
        // be re-joined into a match.
        String letterJoined = ModerationTextNormalizer.joinSpacedLetters(searchable);
        String compact = searchable.replace(" ", "");

        ModerationVerdict verdict = ModerationVerdict.allowed();
        for (CompiledTerm term : restricted) {
            if (!matches(searchable, letterJoined, compact, term)) {
                continue;
            }
            verdict = verdict.merge(term.verdict());
            if (verdict.blocks()) {
                return verdict;
            }
        }
        return verdict;
    }

    /**
     * Three passes, in increasing tolerance and decreasing safety:
     * the text as written, the text with letter-spacing undone, and the text with every
     * space removed. The last pass has no word boundaries left, so it is only allowed for
     * terms long enough that an accidental substring hit is implausible.
     */
    private boolean matches(String searchable, String letterJoined, String compact, CompiledTerm term) {
        return indexOfTerm(searchable, term) >= 0
                || indexOfTerm(letterJoined, term) >= 0
                || (term.compactPattern.length() >= CompiledTerm.MIN_COMPACT_LENGTH
                        && compact.contains(term.compactPattern));
    }

    private int indexOfTerm(String haystack, CompiledTerm term) {
        int from = 0;
        while (from <= haystack.length() - term.pattern.length()) {
            int found = haystack.indexOf(term.pattern, from);
            if (found < 0) {
                return -1;
            }
            if (!term.requiresWordBoundary
                    || ModerationTextNormalizer.isWholeWordMatch(haystack, found, term.pattern.length())) {
                return found;
            }
            from = found + 1;
        }
        return -1;
    }

    private void maskAll(StringBuilder text, CompiledTerm exemption) {
        int from = 0;
        while (true) {
            int found = text.indexOf(exemption.pattern, from);
            if (found < 0) {
                return;
            }
            for (int index = found; index < found + exemption.pattern.length(); index++) {
                if (text.charAt(index) != ' ') {
                    text.setCharAt(index, MASK);
                }
            }
            from = found + exemption.pattern.length();
        }
    }

    /** A term with its normalized patterns resolved once, at load time. */
    private record CompiledTerm(
            java.util.UUID id,
            String original,
            String pattern,
            String compactPattern,
            boolean requiresWordBoundary,
            com.ds.goroute.type.ModerationCategory category,
            ModerationAction action) {

        /** Below this, a space-stripped match is more likely to be an accident than an evasion. */
        private static final int MIN_COMPACT_LENGTH = 5;

        static CompiledTerm from(ModerationTerm term) {
            String pattern = term.getNormalizedTerm() == null || term.getNormalizedTerm().isBlank()
                    ? ModerationTextNormalizer.normalize(term.getTerm())
                    : ModerationTextNormalizer.normalize(term.getNormalizedTerm());
            if (pattern.isEmpty()) {
                return null;
            }
            return new CompiledTerm(
                    term.getId(),
                    term.getTerm(),
                    pattern,
                    pattern.replace(" ", ""),
                    !pattern.contains(" "),
                    term.getCategory(),
                    term.getAction() == null ? ModerationAction.FLAG : term.getAction());
        }

        ModerationVerdict verdict() {
            return ModerationVerdict.of(action, category, id, original);
        }
    }
}

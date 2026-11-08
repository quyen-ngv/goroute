package com.ds.goroute.utils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keyword queries of the place and Feed search indexes, built for precision in Vietnamese
 * (epic 06 §2.3, epic 12 §4.4):
 *
 * <ul>
 *   <li>every word must match — from three words on, a quarter may miss — and only the last
 *       one may match as the start of a word (it is still being typed), so "da lat" no longer
 *       also finds "dao lam";</li>
 *   <li>a word typed with accents also scores on its accented form, so "phở" puts phở above
 *       phố and "Hội An" above "hỏi ăn"; without accents both score alike. The field must be
 *       indexed with {@link #createAnalyzer()}, which keeps both forms;</li>
 *   <li>typos are a fallback ({@link Match#TYPO}), asked only when {@link Match#EXACT} found
 *       nothing: one wrong letter on a four-letter syllable turns "binh" into minh, linh, dinh.</li>
 * </ul>
 */
public final class LuceneKeywordQuery {

    /** How loosely the words may match; callers try them in order and keep the first that finds anything. */
    public enum Match {
        /** Every word, as typed (accents optional), the last one also as a start. */
        EXACT,
        /** {@link #EXACT}, plus one wrong letter (not the first) in words long enough. */
        TYPO,
        /** {@link #TYPO} with any one word enough: only where an area already narrows the search. */
        ANY_WORD
    }

    /** The tiers of a search with no area around it. */
    public static final List<Match> STRICT_TIERS = List.of(Match.EXACT, Match.TYPO);
    /** The tiers of a search inside an area. */
    public static final List<Match> AREA_TIERS = List.of(Match.EXACT, Match.TYPO, Match.ANY_WORD);

    /**
     * Hits scoring under this share of the best one are its weak tail (a word only in a long
     * caption, a folded look-alike) and are dropped. Relative, because scores of two
     * keywords cannot be compared.
     */
    public static final float RELATIVE_SCORE_FLOOR = 0.25f;

    private static final int PHRASE_SLOP = 1;
    /** The first letter of a typo'd word is taken as right. */
    private static final int FUZZY_PREFIX_LENGTH = 1;
    /** A one-word keyword shorter than this is not searched as the start of a word. */
    private static final int MIN_LONE_PREFIX_LENGTH = 2;

    /**
     * One searched field and how much each kind of match in it weighs. A word matches as the
     * start of one when it is the last word and at least {@code minPrefixLength} long, and
     * with a typo when at least {@code minFuzzyLength} long; a zero phrase boost adds no phrase.
     */
    public record Field(String name, float termBoost, float prefixBoost, float fuzzyBoost, float phraseBoost,
                        int minPrefixLength, int minFuzzyLength) {
    }

    /** One word of a keyword: always folded, and as typed when that carried accents (else null). */
    public record Word(String folded, String accented) {
    }

    private LuceneKeywordQuery() {
    }

    /** Index-side analysis: lower case, accents folded, and the accented form kept beside the folded one. */
    public static Analyzer createAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                StandardTokenizer tokenizer = new StandardTokenizer();
                TokenStream stream = new LowerCaseFilter(tokenizer);
                stream = new ASCIIFoldingFilter(stream, true);
                return new TokenStreamComponents(tokenizer, stream);
            }
        };
    }

    /** Composed form, so text typed as letter + combining accent indexes and matches like the rest. */
    public static String normalize(String text) {
        return text == null ? null : Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    /** The words of {@code keyword}, in order, without repeats, at most {@code maxWords}. */
    public static List<Word> words(String keyword, int maxWords) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        Set<Word> words = new LinkedHashSet<>();
        StandardTokenizer tokenizer = new StandardTokenizer();
        try (TokenStream stream = new LowerCaseFilter(tokenizer)) {
            tokenizer.setReader(new StringReader(normalize(keyword)));
            CharTermAttribute attribute = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken() && words.size() < maxWords) {
                String token = attribute.toString();
                String folded = fold(token);
                words.add(new Word(folded, folded.equals(token) ? null : token));
            }
            stream.end();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ArrayList<>(words);
    }

    /**
     * The keyword query over {@code fields}: each word must match in at least one of them
     * (see the class comment), and words standing together in a field, in order, score extra.
     * Null when there are no words.
     */
    public static Query build(List<Word> words, List<Field> fields, Match match) {
        if (words.isEmpty() || fields.isEmpty()) {
            return null;
        }
        int count = words.size();
        BooleanQuery.Builder allWords = new BooleanQuery.Builder();
        for (int index = 0; index < count; index++) {
            boolean last = index == count - 1;
            allWords.add(wordQuery(words.get(index), fields, match, last, count == 1), BooleanClause.Occur.SHOULD);
        }
        allWords.setMinimumNumberShouldMatch(match == Match.ANY_WORD ? 1 : count <= 2 ? count : count - count / 4);

        BooleanQuery.Builder query = new BooleanQuery.Builder().add(allWords.build(), BooleanClause.Occur.MUST);
        if (count > 1) {
            String[] phrase = words.stream().map(Word::folded).toArray(String[]::new);
            for (Field field : fields) {
                if (field.phraseBoost() > 0) {
                    query.add(new BoostQuery(new PhraseQuery(PHRASE_SLOP, field.name(), phrase), field.phraseBoost()),
                            BooleanClause.Occur.SHOULD);
                }
            }
        }
        return query.build();
    }

    /** The leading hits scoring at least {@link #RELATIVE_SCORE_FLOOR} of the best one. */
    public static ScoreDoc[] relevant(ScoreDoc[] hits) {
        if (hits.length == 0) {
            return hits;
        }
        float floor = hits[0].score * RELATIVE_SCORE_FLOOR;
        int kept = 0;
        while (kept < hits.length && hits[kept].score >= floor) {
            kept++;
        }
        return kept == hits.length ? hits : Arrays.copyOf(hits, kept);
    }

    private static Query wordQuery(Word word, List<Field> fields, Match match, boolean last, boolean alone) {
        BooleanQuery.Builder anyField = new BooleanQuery.Builder();
        String folded = word.folded();
        for (Field field : fields) {
            add(anyField, new TermQuery(new Term(field.name(), folded)), field.termBoost());
            if (word.accented() != null) {
                add(anyField, new TermQuery(new Term(field.name(), word.accented())), field.termBoost());
            }
            int minPrefix = alone ? Math.max(MIN_LONE_PREFIX_LENGTH, field.minPrefixLength()) : field.minPrefixLength();
            if (last && folded.length() >= minPrefix) {
                add(anyField, new PrefixQuery(new Term(field.name(), folded)), field.prefixBoost());
            }
            if (match != Match.EXACT && folded.length() >= field.minFuzzyLength()) {
                add(anyField, new FuzzyQuery(new Term(field.name(), folded), 1, FUZZY_PREFIX_LENGTH),
                        field.fuzzyBoost());
            }
        }
        return anyField.build();
    }

    private static void add(BooleanQuery.Builder builder, Query query, float boost) {
        builder.add(new BoostQuery(query, boost), BooleanClause.Occur.SHOULD);
    }

    private static String fold(String token) {
        char[] input = token.toCharArray();
        char[] output = new char[input.length * 4];
        int length = ASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
        return new String(output, 0, length);
    }
}

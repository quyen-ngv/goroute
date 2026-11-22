package com.ds.goroute.utils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.icu.segmentation.DefaultICUTokenizerConfig;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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
 *   <li>typos are a fallback ({@link Match#TYPO}, then {@link Match#LOOSE}), asked only when
 *       the tier before found nothing: one wrong letter on a four-letter syllable turns "binh"
 *       into minh, linh, dinh.</li>
 *   <li>words typed without their space, or with one too many, still match: "hanoi" finds
 *       "Hà Nội" through a field of {@link #joinedWords joined words}, and "ha noi" finds
 *       "Hanoi" because two neighbouring words are also tried written together.</li>
 *   <li>Thai, Lao and Khmer are split into words by dictionary, and Chinese, Japanese and Korean
 *       into overlapping pairs of characters ("还剑湖" → 还剑, 剑湖), on both the index and the
 *       keyword side, so "还剑" or "호안끼엠" finds Hồ Hoàn Kiếm by its translated name.</li>
 *   <li>a place search ({@link Extras}) lets a word be explained by where or what the place is
 *       as well as by its name: "phở mai nhà chung" finds the Phở Mai on Nhà Chung street. Such
 *       fields match by pairs of neighbouring words ({@link TermField}), because one Vietnamese
 *       syllable of an address is in thousands of them.</li>
 * </ul>
 */
public final class LuceneKeywordQuery {

    /** How loosely the words may match; callers try them in order and keep the first that finds anything. */
    public enum Match {
        /**
         * {@link #EXACT} with no word left out: every word of the keyword is explained, by a
         * name or, in a place search, by where or what the place is ({@link Extras}).
         */
        ALL,
        /**
         * The words as typed (accents optional), the last one also as a start; from three words
         * on, a quarter of them may miss.
         */
        EXACT,
        /** {@link #EXACT}, plus one wrong letter (not the first) in words long enough. */
        TYPO,
        /**
         * {@link #TYPO} for worse typing: the first letter may be the wrong one, and a word of
         * {@link #LOOSE_TWO_EDITS_LENGTH} letters or more may have two wrong letters. Every word
         * is still required.
         */
        LOOSE,
        /** {@link #TYPO} with any one word enough: only where an area already narrows the search. */
        ANY_WORD
    }

    /** The tiers of a search with no area around it. */
    public static final List<Match> STRICT_TIERS = List.of(Match.EXACT, Match.TYPO);
    /** The tiers of a search by a place's name with no area around it. */
    public static final List<Match> NAME_TIERS = List.of(Match.ALL, Match.EXACT, Match.TYPO, Match.LOOSE);
    /** The tiers of a search inside an area. */
    public static final List<Match> AREA_TIERS =
            List.of(Match.ALL, Match.EXACT, Match.TYPO, Match.LOOSE, Match.ANY_WORD);

    /**
     * Hits scoring under this share of the best one are its weak tail (a word only in a long
     * caption, a folded look-alike) and are dropped. Relative, because scores of two
     * keywords cannot be compared.
     */
    public static final float RELATIVE_SCORE_FLOOR = 0.25f;

    /** Joins two neighbouring words into one term of a {@link TermField}: "nha_chung". */
    public static final char PAIR_SEPARATOR = '_';

    private static final int PHRASE_SLOP = 1;
    /** The first letter of a typo'd word is taken as right. */
    private static final int FUZZY_PREFIX_LENGTH = 1;
    /** A one-word keyword shorter than this is not searched as the start of a word. */
    private static final int MIN_LONE_PREFIX_LENGTH = 2;
    /** {@link Match#LOOSE}: the shortest word matched with one wrong letter anywhere in it. */
    private static final int LOOSE_ONE_EDIT_LENGTH = 4;
    /** {@link Match#LOOSE}: the shortest word matched with two wrong letters. */
    static final int LOOSE_TWO_EDITS_LENGTH = 8;
    /** Keywords longer than this are not also tried with two neighbouring words joined. */
    private static final int MAX_WORDS_FOR_JOINING = 6;
    /** How many words of a name {@link #joinedWords} reads. */
    private static final int MAX_JOINED_SOURCE_WORDS = 24;
    /** A pair of words shorter than this, separator included, is not matched with a typo. */
    private static final int MIN_FUZZY_PAIR_LENGTH = 6;
    /** A word shorter than this is not matched as the start of a {@link TermField} single. */
    private static final int MIN_TERM_PREFIX_LENGTH = 3;
    /**
     * From this many words on, the last one may also be the first word of a pair, or the start
     * of a single, still being typed. In a shorter keyword it would explain too much: "phở mai"
     * would find every phở of a street named Mai Hắc Đế.
     */
    private static final int MIN_WORDS_FOR_TYPED_START = 3;
    /** The longest run of words looked up as a whole name. */
    private static final int MAX_EXACT_SPAN = 6;
    private static final float TERM_PREFIX_FACTOR = 0.5f;
    private static final float TERM_FUZZY_FACTOR = 0.4f;

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
        /** Latin letters and digits once folded: the only words matched with a typo. */
        public boolean latin() {
            return folded.chars().allMatch(character -> character < 128);
        }
    }

    /**
     * A field of whole terms rather than analysed text, as {@link PlaceAddressTerms} and
     * {@link PlaceCategoryTerms} build them: {@code pairs} holds neighbouring words joined by
     * {@link #PAIR_SEPARATOR}, {@code singles} the values that are one word. A word of the
     * keyword matches by a pair it forms with the word before or after it, or as a single. An
     * {@code area} field says where the place is; the word after a unit word ("phường cửa nam")
     * matches only there.
     */
    public record TermField(String pairs, String singles, float boost, boolean area) {
    }

    /**
     * What a place search adds to the analysed fields. A word may also be explained by a
     * {@link TermField}, or, when it starts with a digit, by {@code numberField} (a house
     * number). A run of words that is a whole name in {@code exactField} (the folded words
     * joined by a space) adds {@code exactBoost} per word. {@code unitWords} tells which words
     * may be unit words ({@link PlaceAddressTerms#markers}); the keyword is then also tried
     * without them.
     */
    public record Extras(List<TermField> termFields, String numberField, float numberBoost,
                         String exactField, float exactBoost, Function<List<Word>, boolean[]> unitWords) {
    }

    /** One word of a keyword as a query reads it: free, or bound to the area fields. */
    private record Slot(Word word, boolean areaOnly) {
    }

    /** {@link #createAnalyzer()} without the folding: {@link #words} folds each token itself. */
    private static final Analyzer KEYWORD_ANALYZER = new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = createTokenizer();
            return new TokenStreamComponents(tokenizer, splitAndLowerCase(tokenizer));
        }
    };

    private LuceneKeywordQuery() {
    }

    /**
     * Index-side analysis: words split by script (dictionary words for Thai, Lao, Khmer; pairs
     * of characters for Han, kana and Hangul), lower case, accents folded, and the accented form
     * kept beside the folded one.
     */
    public static Analyzer createAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = createTokenizer();
                return new TokenStreamComponents(tokenizer,
                        new ASCIIFoldingFilter(splitAndLowerCase(tokenizer), true));
            }
        };
    }

    /** Han and kana one character per token (not dictionary words), for the bigram filter to pair. */
    private static Tokenizer createTokenizer() {
        return new ICUTokenizer(new DefaultICUTokenizerConfig(false, true));
    }

    private static TokenStream splitAndLowerCase(Tokenizer tokenizer) {
        TokenStream stream = new CJKBigramFilter(tokenizer,
                CJKBigramFilter.HAN | CJKBigramFilter.HIRAGANA | CJKBigramFilter.KATAKANA | CJKBigramFilter.HANGUL);
        return new LowerCaseFilter(stream);
    }

    /** Composed form, so text typed as letter + combining accent indexes and matches like the rest. */
    public static String normalize(String text) {
        return text == null ? null : Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    /** The words of {@code keyword}, in order, without repeats, at most {@code maxWords}. */
    public static List<Word> words(String keyword, int maxWords) {
        return new ArrayList<>(read(keyword, maxWords, new LinkedHashSet<>()));
    }

    /** The words of {@code text}, in order, repeats kept, at most {@code maxWords}. */
    public static List<Word> tokens(String text, int maxWords) {
        return new ArrayList<>(read(text, maxWords, new ArrayList<>()));
    }

    private static Collection<Word> read(String text, int maxWords, Collection<Word> words) {
        if (text == null || text.isBlank()) {
            return words;
        }
        try (TokenStream stream = KEYWORD_ANALYZER.tokenStream("", normalize(text))) {
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
        return words;
    }

    /**
     * Neighbouring Latin words of {@code name} written together, two and three at a time
     * ("Hà Nội" → "hanoi", "Hồ Hoàn Kiếm" → "hohoan", "hoankiem", "hohoankiem"), folded. Indexed
     * in a field of its own, they let a keyword typed without its spaces find the name.
     */
    public static Set<String> joinedWords(String name) {
        List<Word> words = words(name, MAX_JOINED_SOURCE_WORDS);
        Set<String> joined = new LinkedHashSet<>();
        for (int index = 0; index + 1 < words.size(); index++) {
            Word first = words.get(index);
            Word second = words.get(index + 1);
            if (!first.latin() || !second.latin()) {
                continue;
            }
            String pair = first.folded() + second.folded();
            joined.add(pair);
            if (index + 2 < words.size() && words.get(index + 2).latin()) {
                joined.add(pair + words.get(index + 2).folded());
            }
        }
        return joined;
    }

    /**
     * The keyword query over {@code fields}: each word must match in at least one of them
     * (see the class comment), and words standing together in a field, in order, score extra.
     * A keyword of a few Latin words is also tried with each pair of neighbours written
     * together, so "ha noi" finds a name that says "Hanoi". Null when there are no words.
     */
    public static Query build(List<Word> words, List<Field> fields, Match match) {
        return build(words, fields, null, match);
    }

    /**
     * {@link #build(List, List, Match)} for a search that also reads whole-term fields
     * ({@link Extras}). {@link Match#ANY_WORD} reads the analysed fields only: one word of an
     * address would match a whole city.
     */
    public static Query build(List<Word> words, List<Field> fields, Extras extras, Match match) {
        if (words.isEmpty() || fields.isEmpty()) {
            return null;
        }
        Extras used = match == Match.ANY_WORD ? null : extras;
        List<Query> spellings = new ArrayList<>();
        spellings.add(buildSlots(free(words), fields, used, match, true));
        if (words.size() >= 2 && words.size() <= MAX_WORDS_FOR_JOINING) {
            for (int index = 0; index + 1 < words.size(); index++) {
                Word first = words.get(index);
                Word second = words.get(index + 1);
                if (!first.latin() || !second.latin()) {
                    continue;
                }
                List<Word> joined = new ArrayList<>(words);
                joined.set(index, new Word(first.folded() + second.folded(), null));
                joined.remove(index + 1);
                spellings.add(buildSlots(free(joined), fields, used, match, false));
            }
        }
        List<Slot> withoutUnitWords = withoutUnitWords(words, used);
        if (withoutUnitWords != null) {
            spellings.add(buildSlots(withoutUnitWords, fields, used, match, true));
        }
        if (spellings.size() == 1) {
            return spellings.get(0);
        }
        BooleanQuery.Builder anySpelling = new BooleanQuery.Builder();
        spellings.forEach(spelling -> anySpelling.add(spelling, BooleanClause.Occur.SHOULD));
        return anySpelling.build();
    }

    /**
     * {@code tiers} for a keyword of {@code wordCount} words: up to two words {@link Match#ALL}
     * and {@link Match#EXACT} are the same query, so the first is left out.
     */
    public static List<Match> tiersFor(List<Match> tiers, int wordCount) {
        return wordCount > 2 ? tiers : tiers.stream().filter(match -> match != Match.ALL).toList();
    }

    private static List<Slot> free(List<Word> words) {
        return words.stream().map(word -> new Slot(word, false)).toList();
    }

    /**
     * The keyword without its unit words, the word after each bound to the area fields: in
     * "phở mai phường cửa nam", "phường" is dropped and "cửa" must be part of where the place
     * is. Null when the keyword has none, or nothing would be left to search by.
     */
    private static List<Slot> withoutUnitWords(List<Word> words, Extras extras) {
        if (extras == null || extras.unitWords() == null
                || extras.termFields().stream().noneMatch(TermField::area)) {
            return null;
        }
        boolean[] unitWords = extras.unitWords().apply(words);
        List<Slot> slots = new ArrayList<>();
        boolean any = false;
        for (int index = 0; index < words.size(); index++) {
            if (unitWords[index]) {
                any = true;
                continue;
            }
            slots.add(new Slot(words.get(index), index > 0 && unitWords[index - 1]));
        }
        return any && !slots.isEmpty() ? slots : null;
    }

    /**
     * One spelling of the keyword. {@code termTypos}: whether a typo is also forgiven in the
     * area fields; left out of the spellings that only join two words, where it would add an
     * automaton per word for nothing.
     */
    private static Query buildSlots(List<Slot> slots, List<Field> fields, Extras extras, Match match,
                                    boolean termTypos) {
        int count = slots.size();
        BooleanQuery.Builder allWords = new BooleanQuery.Builder();
        for (int index = 0; index < count; index++) {
            allWords.add(slotQuery(slots, index, fields, extras, match, termTypos), BooleanClause.Occur.SHOULD);
        }
        allWords.setMinimumNumberShouldMatch(switch (match) {
            case ANY_WORD -> 1;
            case ALL -> count;
            default -> count <= 2 ? count : count - count / 4;
        });

        BooleanQuery.Builder query = new BooleanQuery.Builder().add(allWords.build(), BooleanClause.Occur.MUST);
        if (count > 1) {
            String[] phrase = slots.stream().map(slot -> slot.word().folded()).toArray(String[]::new);
            for (Field field : fields) {
                if (field.phraseBoost() > 0) {
                    query.add(new BoostQuery(new PhraseQuery(PHRASE_SLOP, field.name(), phrase), field.phraseBoost()),
                            BooleanClause.Occur.SHOULD);
                }
            }
        }
        Query wholeName = wholeNameQuery(slots, extras);
        if (wholeName != null) {
            query.add(wholeName, BooleanClause.Occur.SHOULD);
        }
        return query.build();
    }

    /**
     * A bonus for the longest run of the keyword's words that is a whole name: "phở mai" in
     * "phở mai hà nội" lifts the place named Phở Mai over one whose long name merely holds the
     * four words. Null when the search has no exact-name field.
     */
    private static Query wholeNameQuery(List<Slot> slots, Extras extras) {
        if (extras == null || extras.exactField() == null) {
            return null;
        }
        List<Query> runs = new ArrayList<>();
        for (int start = 0; start < slots.size(); start++) {
            StringBuilder run = new StringBuilder();
            for (int end = start; end < slots.size() && end - start < MAX_EXACT_SPAN; end++) {
                if (end > start) {
                    run.append(' ');
                }
                run.append(slots.get(end).word().folded());
                runs.add(new BoostQuery(
                        new ConstantScoreQuery(new TermQuery(new Term(extras.exactField(), run.toString()))),
                        extras.exactBoost() * (end - start + 1)));
            }
        }
        return new DisjunctionMaxQuery(runs, 0f);
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

    private static Query slotQuery(List<Slot> slots, int index, List<Field> fields, Extras extras, Match match,
                                   boolean termTypos) {
        Slot slot = slots.get(index);
        Word word = slot.word();
        String folded = word.folded();
        int count = slots.size();
        boolean last = index == count - 1;
        boolean alone = count == 1;
        BooleanQuery.Builder anyField = new BooleanQuery.Builder();
        if (!slot.areaOnly()) {
            for (Field field : fields) {
                add(anyField, new TermQuery(new Term(field.name(), folded)), field.termBoost());
                if (word.accented() != null) {
                    add(anyField, new TermQuery(new Term(field.name(), word.accented())), field.termBoost());
                }
                int minPrefix = alone ? Math.max(MIN_LONE_PREFIX_LENGTH, field.minPrefixLength()) : field.minPrefixLength();
                if (last && folded.length() >= minPrefix) {
                    add(anyField, new PrefixQuery(new Term(field.name(), folded)), field.prefixBoost());
                }
                Query typo = typoQuery(field, folded, word.latin(), match);
                if (typo != null) {
                    add(anyField, typo, field.fuzzyBoost());
                }
            }
        }
        if (extras != null) {
            String before = index > 0 ? slots.get(index - 1).word().folded() + PAIR_SEPARATOR + folded : null;
            String after = last ? null : folded + PAIR_SEPARATOR + slots.get(index + 1).word().folded();
            boolean typos = termTypos && (match == Match.TYPO || match == Match.LOOSE) && word.latin();
            // A word still being typed is weak evidence: read it so only in a longer keyword
            // of which no word may miss.
            boolean typing = last && match == Match.ALL && count >= MIN_WORDS_FOR_TYPED_START;
            for (TermField field : extras.termFields()) {
                if (slot.areaOnly() && !field.area()) {
                    continue;
                }
                addTermMatches(anyField, field, folded, before, after, last, typing, typos && field.area());
            }
            if (extras.numberField() != null && !slot.areaOnly() && Character.isDigit(folded.charAt(0))) {
                add(anyField, new TermQuery(new Term(extras.numberField(), folded)), extras.numberBoost());
            }
        }
        return anyField.build();
    }

    /**
     * How one word matches a {@link TermField}: as a single, or by the pair it forms with the
     * word before or after it. The last word also as the end of a pair still being typed and,
     * when {@code typing}, as the start of a pair or of a single; with a typo when the tier
     * forgives one.
     */
    private static void addTermMatches(BooleanQuery.Builder anyField, TermField field, String folded,
                                       String before, String after, boolean last, boolean typing,
                                       boolean typos) {
        float boost = field.boost();
        add(anyField, new TermQuery(new Term(field.singles(), folded)), boost);
        if (before != null) {
            add(anyField, new TermQuery(new Term(field.pairs(), before)), boost);
        }
        if (after != null) {
            add(anyField, new TermQuery(new Term(field.pairs(), after)), boost);
        }
        if (last && before != null) {
            add(anyField, new PrefixQuery(new Term(field.pairs(), before)), boost * TERM_PREFIX_FACTOR);
        }
        if (typing) {
            add(anyField, new PrefixQuery(new Term(field.pairs(), folded + PAIR_SEPARATOR)),
                    boost * TERM_PREFIX_FACTOR);
            if (folded.length() >= MIN_TERM_PREFIX_LENGTH) {
                add(anyField, new PrefixQuery(new Term(field.singles(), folded)), boost * TERM_PREFIX_FACTOR);
            }
        }
        if (typos) {
            if (before != null && before.length() >= MIN_FUZZY_PAIR_LENGTH) {
                add(anyField, new FuzzyQuery(new Term(field.pairs(), before), 1, FUZZY_PREFIX_LENGTH),
                        boost * TERM_FUZZY_FACTOR);
            }
            if (after != null && after.length() >= MIN_FUZZY_PAIR_LENGTH) {
                add(anyField, new FuzzyQuery(new Term(field.pairs(), after), 1, FUZZY_PREFIX_LENGTH),
                        boost * TERM_FUZZY_FACTOR);
            }
            if (folded.length() >= LOOSE_ONE_EDIT_LENGTH) {
                add(anyField, new FuzzyQuery(new Term(field.singles(), folded), 1, FUZZY_PREFIX_LENGTH),
                        boost * TERM_FUZZY_FACTOR);
            }
        }
    }

    /** The word with the wrong letters {@code match} forgives, in {@code field}; null when it forgives none. */
    private static Query typoQuery(Field field, String folded, boolean latin, Match match) {
        if (match == Match.ALL || match == Match.EXACT || !latin) {
            return null;
        }
        Term term = new Term(field.name(), folded);
        if (match == Match.LOOSE) {
            if (folded.length() < LOOSE_ONE_EDIT_LENGTH) {
                return null;
            }
            return new FuzzyQuery(term, folded.length() >= LOOSE_TWO_EDITS_LENGTH ? 2 : 1, 0);
        }
        return folded.length() >= field.minFuzzyLength() ? new FuzzyQuery(term, 1, FUZZY_PREFIX_LENGTH) : null;
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

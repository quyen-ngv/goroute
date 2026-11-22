package com.ds.goroute.utils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LuceneKeywordQueryAnalyzerTest {

    private final Analyzer analyzer = LuceneKeywordQuery.createAnalyzer();

    @AfterEach
    void close() {
        analyzer.close();
    }

    @Test
    void chineseIsIndexedAsPairsOfCharacters() throws IOException {
        assertThat(terms("还剑湖")).containsExactly("还剑", "剑湖");
    }

    @Test
    void koreanAndJapaneseAreIndexedAsPairsToo() throws IOException {
        assertThat(terms("호안끼엠")).containsExactly("호안", "안끼", "끼엠");
        assertThat(terms("ホアンキエム")).containsExactly("ホア", "アン", "ンキ", "キエ", "エム");
    }

    @Test
    void thaiIsSplitIntoWords() throws IOException {
        List<String> terms = terms("ทะเลสาบฮหว่านเกี๊ยม");
        assertThat(terms).hasSizeGreaterThan(1)
                .allSatisfy(term -> assertThat(term).matches("\\p{IsThai}+"));
    }

    @Test
    void vietnameseKeepsTheFoldedAndTheAccentedForm() throws IOException {
        assertThat(terms("Hồ Hoàn Kiếm")).containsExactlyInAnyOrder("ho", "hồ", "hoan", "hoàn", "kiem", "kiếm");
    }

    @Test
    void keywordWordsAreTheIndexedTerms() throws IOException {
        List<String> indexed = terms("Hồ Hoàn Kiếm 还剑湖 호안끼엠호 ทะเลสาบฮหว่านเกี๊ยม");

        assertThat(folded("还剑")).containsExactly("还剑");
        assertThat(indexed).contains("还剑");
        assertThat(indexed).containsAll(folded("호안끼엠"));
        assertThat(indexed).containsAll(folded("ทะเลสาบฮหว่านเกี๊ยม"));
        assertThat(indexed).containsAll(folded("ho hoan kiem"));
    }

    @Test
    void onlyLatinWordsMayMatchWithATypo() {
        assertThat(LuceneKeywordQuery.words("Hồ Gươm", 8))
                .extracting(LuceneKeywordQuery.Word::folded, LuceneKeywordQuery.Word::accented)
                .containsExactly(
                        tuple("ho", "hồ"),
                        tuple("guom", "gươm"));
        assertThat(LuceneKeywordQuery.words("Hồ Gươm", 8)).allMatch(LuceneKeywordQuery.Word::latin);
        assertThat(LuceneKeywordQuery.words("还剑湖 호안끼엠", 8)).noneMatch(LuceneKeywordQuery.Word::latin);
    }

    private List<String> folded(String keyword) {
        return LuceneKeywordQuery.words(keyword, 20).stream().map(LuceneKeywordQuery.Word::folded).toList();
    }

    private List<String> terms(String text) throws IOException {
        List<String> terms = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream("name", LuceneKeywordQuery.normalize(text))) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                terms.add(term.toString());
            }
            stream.end();
        }
        return terms;
    }
}

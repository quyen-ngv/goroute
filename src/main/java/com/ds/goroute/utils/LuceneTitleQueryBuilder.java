package com.ds.goroute.utils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Builds flexible name-only Lucene queries: token match, prefix, substring, fuzzy.
 */
public final class LuceneTitleQueryBuilder {

    private static final String NAME_FIELD = "name";
    private static final float PHRASE_BOOST = 10.0f;
    private static final float TOKEN_QUERY_BOOST = 4.0f;
    private static final float PREFIX_BOOST = 2.0f;
    private static final float FUZZY_BOOST = 0.75f;
    private static final float SUBSTRING_BOOST = 0.25f;

    private LuceneTitleQueryBuilder() {
    }

    public static Query build(String rawQuery, Analyzer analyzer) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Empty query");
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        QueryParser parser = new QueryParser(NAME_FIELD, analyzer);
        parser.setDefaultOperator(QueryParser.Operator.OR);
        parser.setAllowLeadingWildcard(true);

        try {
            String escapedQuery = QueryParser.escape(query);
            builder.add(new BoostQuery(parser.parse(escapedQuery), TOKEN_QUERY_BOOST), BooleanClause.Occur.SHOULD);
            if (query.chars().anyMatch(Character::isWhitespace)) {
                builder.add(new BoostQuery(parser.parse("\"" + escapedQuery + "\""), PHRASE_BOOST),
                        BooleanClause.Occur.SHOULD);
            }
        } catch (ParseException ignored) {
            // Fall back to prefix / wildcard / fuzzy clauses below.
        }

        String[] tokens = query.toLowerCase(Locale.ROOT).split("\\s+");
        for (String token : tokens) {
            String normalized = normalizeToken(token);
            if (normalized.length() < 2) {
                continue;
            }

            builder.add(new BoostQuery(new PrefixQuery(new Term(NAME_FIELD, normalized)), PREFIX_BOOST),
                    BooleanClause.Occur.SHOULD);
            builder.add(
                    new BoostQuery(new WildcardQuery(new Term(NAME_FIELD, "*" + normalized + "*")),
                            SUBSTRING_BOOST),
                    BooleanClause.Occur.SHOULD);

            if (normalized.length() >= 4) {
                builder.add(new BoostQuery(new FuzzyQuery(new Term(NAME_FIELD, normalized), 1), FUZZY_BOOST),
                        BooleanClause.Occur.SHOULD);
            }
        }

        BooleanQuery booleanQuery = builder.build();
        if (booleanQuery.clauses().isEmpty()) {
            String fallback = normalizeToken(query.toLowerCase(Locale.ROOT));
            if (fallback.isEmpty()) {
                throw new IllegalArgumentException("Query contains no searchable characters");
            }
            return new WildcardQuery(new Term(NAME_FIELD, "*" + fallback + "*"));
        }

        return booleanQuery;
    }

    private static String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String cleaned = token.replace("\\", "")
                .replace("*", "")
                .replace("?", "")
                .trim();
        return Normalizer.normalize(cleaned, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'd');
    }
}

package com.ds.goroute.service.impl;

import com.ds.goroute.dto.SocialSearchHits;
import com.ds.goroute.entity.SocialSearchIndexRow;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.SocialSearchIndexService;
import com.ds.goroute.type.SocialSearchType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialSearchIndexServiceImpl implements SocialSearchIndexService {

    private static final int BATCH_SIZE = 500;
    /** Pages one catch-up pass reads per kind at most; a bigger backlog carries over to the next pass. */
    private static final int MAX_CATCH_UP_PAGES = 20;
    /**
     * A row stamped just before a pass can commit just after it, behind the cursor. Every pass
     * starts again this far before the newest change it saw; re-reading a row is harmless.
     */
    private static final Duration CATCH_UP_OVERLAP = Duration.ofMinutes(2);
    private static final UUID MIN_UUID = new UUID(0L, 0L);
    private static final LocalDateTime BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final int MAX_QUERY_TERMS = 8;
    private static final int MAX_BODY_CHARS = 4_000;

    private static final String KEY_FIELD = "key";
    private static final String TYPE_FIELD = "type";
    private static final String ID_FIELD = "id";
    private static final String TITLE_FIELD = "title";
    private static final String HANDLE_FIELD = "handle";
    private static final String HANDLE_EXACT_FIELD = "handle_exact";
    private static final String BODY_FIELD = "body";
    private static final String PUBLISHED_FIELD = "published";
    private static final String FEATURES_FIELD = "features";
    private static final String POPULARITY_FEATURE = "popularity";
    /** Marks the documents of one rebuild, so it can drop every document it did not write. */
    private static final String GENERATION_FIELD = "generation";

    // Text: a match in the name outweighs one in the caption; a whole username wins outright.
    private static final float HANDLE_EXACT_BOOST = 20f;
    private static final float TITLE_TERM_BOOST = 4f;
    private static final float TITLE_PREFIX_BOOST = 2f;
    private static final float TITLE_FUZZY_BOOST = 1f;
    private static final float HANDLE_TERM_BOOST = 5f;
    private static final float HANDLE_PREFIX_BOOST = 3f;
    private static final float HANDLE_FUZZY_BOOST = 1f;
    private static final float BODY_TERM_BOOST = 1.5f;
    private static final float BODY_PREFIX_BOOST = 0.5f;
    private static final float BODY_FUZZY_BOOST = 0.3f;
    private static final float TITLE_PHRASE_BOOST = 6f;
    private static final float BODY_PHRASE_BOOST = 2f;
    // Signals: each adds less than 1, so they order equal matches and never beat a better one.
    private static final float POPULARITY_WEIGHT = 1f;
    private static final float POPULARITY_PIVOT = 10f;
    private static final float FRESHNESS_WEIGHT = 1f;
    private static final long FRESHNESS_PIVOT_MILLIS = TimeUnit.DAYS.toMillis(30);

    private static final List<SocialSearchType> INDEXED_KINDS =
            List.of(SocialSearchType.USER, SocialSearchType.CHECKIN, SocialSearchType.TRIP);

    private final UserRepository userRepository;
    private final UserCheckinRepository checkinRepository;
    private final TripRepository tripRepository;

    @Value("${goroute.search.social-index-path:data/lucene-index/social}")
    private String indexPath;

    private Directory directory;
    private IndexWriter writer;
    private SearcherManager searcherManager;
    private Analyzer analyzer;
    private ExecutorService reindexExecutor;

    /** Held by a rebuild and by a catch-up pass for their whole run: never both at once. */
    private final ReentrantLock syncLock = new ReentrantLock();
    private final AtomicBoolean reindexRunning = new AtomicBoolean();
    /** Guarded by {@link #syncLock}. Empty until the first rebuild: nothing to catch up on before it. */
    private final Map<SocialSearchType, LocalDateTime> changedUntil = new EnumMap<>(SocialSearchType.class);
    /** Guarded by {@link #syncLock}: the generation catch-up writes stamp. */
    private String generation = UUID.randomUUID().toString();

    @PostConstruct
    public void init() throws IOException {
        analyzer = createAnalyzer();
        Path indexDir = Paths.get(indexPath);
        Files.createDirectories(indexDir);
        directory = FSDirectory.open(indexDir);
        writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
        searcherManager = new SearcherManager(writer, new SearcherFactory());
        reindexExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "social-search-reindex");
            thread.setDaemon(true);
            return thread;
        });
        log.info("Social search Lucene index initialized at: {}", indexDir.toAbsolutePath());
        // The index on disk keeps answering while it is rebuilt; what changed while the app was
        // down (or by hand in the database) is picked up here.
        triggerReindex();
    }

    /** Same folding as the place index: case and Vietnamese diacritics never stop a match. */
    private static Analyzer createAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                StandardTokenizer tokenizer = new StandardTokenizer();
                TokenStream stream = new LowerCaseFilter(tokenizer);
                stream = new ASCIIFoldingFilter(stream);
                return new TokenStreamComponents(tokenizer, stream);
            }
        };
    }

    @PreDestroy
    public void cleanup() throws IOException {
        if (reindexExecutor != null) {
            reindexExecutor.shutdownNow();
            try {
                reindexExecutor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (searcherManager != null) {
            searcherManager.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (directory != null) {
            directory.close();
        }
        if (analyzer != null) {
            analyzer.close();
        }
    }

    // --- searching -----------------------------------------------------------------------

    @Override
    public SocialSearchHits search(String keyword, Set<SocialSearchType> kinds, UUID excludeUserId,
                                   int offset, int limit) throws IOException {
        if (kinds == null || kinds.isEmpty() || offset < 0 || limit < 1 || offset >= MAX_RESULT_WINDOW) {
            return SocialSearchHits.EMPTY;
        }
        Query text = textQuery(keyword);
        if (text == null) {
            return SocialSearchHits.EMPTY;
        }
        int wanted = Math.min(offset + limit, MAX_RESULT_WINDOW);
        Query query = withRanking(filtered(text, kinds, excludeUserId));

        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs top = searcher.search(query, wanted);
            StoredFields stored = searcher.storedFields();
            List<SocialSearchHits.Hit> hits = new ArrayList<>(Math.max(0, top.scoreDocs.length - offset));
            for (int index = offset; index < top.scoreDocs.length; index++) {
                ScoreDoc scoreDoc = top.scoreDocs[index];
                Document doc = stored.document(scoreDoc.doc, Set.of(TYPE_FIELD, ID_FIELD));
                hits.add(new SocialSearchHits.Hit(
                        SocialSearchType.valueOf(doc.get(TYPE_FIELD)), UUID.fromString(doc.get(ID_FIELD))));
            }
            return new SocialSearchHits(hits, top.totalHits.value);
        } finally {
            searcherManager.release(searcher);
        }
    }

    @Override
    public Map<SocialSearchType, Integer> countByKind(String keyword, UUID excludeUserId) throws IOException {
        Map<SocialSearchType, Integer> counts = new LinkedHashMap<>();
        INDEXED_KINDS.forEach(kind -> counts.put(kind, 0));
        Query text = textQuery(keyword);
        if (text == null) {
            return counts;
        }
        IndexSearcher searcher = searcherManager.acquire();
        try {
            for (SocialSearchType kind : INDEXED_KINDS) {
                counts.put(kind, searcher.count(filtered(text, Set.of(kind), excludeUserId)));
            }
            return counts;
        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * Every word of the keyword (up to {@link #MAX_QUERY_TERMS}) must match somewhere — as a
     * word, the start of one, or one typo away — except that past two words a quarter may miss,
     * so one stray word does not empty the results. Null when the keyword has no word in it.
     */
    private Query textQuery(String keyword) throws IOException {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        List<String> terms = analyze(keyword);
        if (terms.isEmpty()) {
            return null;
        }
        BooleanQuery.Builder words = new BooleanQuery.Builder();
        for (String term : terms) {
            words.add(termQuery(term), BooleanClause.Occur.SHOULD);
        }
        int count = terms.size();
        words.setMinimumNumberShouldMatch(count <= 2 ? count : count - count / 4);

        BooleanQuery.Builder query = new BooleanQuery.Builder()
                .add(words.build(), BooleanClause.Occur.MUST);
        if (count > 1) {
            String[] phrase = terms.toArray(String[]::new);
            query.add(new BoostQuery(new PhraseQuery(1, TITLE_FIELD, phrase), TITLE_PHRASE_BOOST),
                    BooleanClause.Occur.SHOULD);
            query.add(new BoostQuery(new PhraseQuery(1, BODY_FIELD, phrase), BODY_PHRASE_BOOST),
                    BooleanClause.Occur.SHOULD);
        }
        String handle = normalizeHandle(keyword);
        if (!handle.isEmpty()) {
            query.add(new BoostQuery(new TermQuery(new Term(HANDLE_EXACT_FIELD, handle)), HANDLE_EXACT_BOOST),
                    BooleanClause.Occur.SHOULD);
        }
        return query.build();
    }

    /** One word of the keyword, in any field, scored by where and how well it matched. */
    private static Query termQuery(String term) {
        BooleanQuery.Builder anyField = new BooleanQuery.Builder();
        addBoosted(anyField, new TermQuery(new Term(TITLE_FIELD, term)), TITLE_TERM_BOOST);
        addBoosted(anyField, new TermQuery(new Term(HANDLE_FIELD, term)), HANDLE_TERM_BOOST);
        addBoosted(anyField, new TermQuery(new Term(BODY_FIELD, term)), BODY_TERM_BOOST);
        if (term.length() >= 2) {
            addBoosted(anyField, new PrefixQuery(new Term(TITLE_FIELD, term)), TITLE_PREFIX_BOOST);
            addBoosted(anyField, new PrefixQuery(new Term(HANDLE_FIELD, term)), HANDLE_PREFIX_BOOST);
        }
        if (term.length() >= 3) {
            addBoosted(anyField, new PrefixQuery(new Term(BODY_FIELD, term)), BODY_PREFIX_BOOST);
        }
        if (term.length() >= 4) {
            addBoosted(anyField, new FuzzyQuery(new Term(TITLE_FIELD, term), 1), TITLE_FUZZY_BOOST);
            addBoosted(anyField, new FuzzyQuery(new Term(HANDLE_FIELD, term), 1), HANDLE_FUZZY_BOOST);
        }
        if (term.length() >= 5) {
            addBoosted(anyField, new FuzzyQuery(new Term(BODY_FIELD, term), 1), BODY_FUZZY_BOOST);
        }
        return anyField.build();
    }

    private static void addBoosted(BooleanQuery.Builder builder, Query query, float boost) {
        builder.add(new BoostQuery(query, boost), BooleanClause.Occur.SHOULD);
    }

    private static Query filtered(Query text, Set<SocialSearchType> kinds, UUID excludeUserId) {
        BooleanQuery.Builder query = new BooleanQuery.Builder()
                .add(text, BooleanClause.Occur.MUST)
                .add(new TermInSetQuery(TYPE_FIELD, kinds.stream().map(kind -> new BytesRef(kind.name())).toList()),
                        BooleanClause.Occur.FILTER);
        if (excludeUserId != null) {
            query.add(new TermQuery(new Term(KEY_FIELD, key(SocialSearchType.USER, excludeUserId))),
                    BooleanClause.Occur.MUST_NOT);
        }
        return query.build();
    }

    /** Among equal matches: more followed, liked or voted, and newer, come first. */
    private static Query withRanking(Query query) {
        return new BooleanQuery.Builder()
                .add(query, BooleanClause.Occur.MUST)
                .add(FeatureField.newSaturationQuery(FEATURES_FIELD, POPULARITY_FEATURE,
                        POPULARITY_WEIGHT, POPULARITY_PIVOT), BooleanClause.Occur.SHOULD)
                .add(LongField.newDistanceFeatureQuery(PUBLISHED_FIELD, FRESHNESS_WEIGHT,
                        System.currentTimeMillis(), FRESHNESS_PIVOT_MILLIS), BooleanClause.Occur.SHOULD)
                .build();
    }

    private List<String> analyze(String text) throws IOException {
        Set<String> terms = new LinkedHashSet<>();
        try (TokenStream stream = analyzer.tokenStream(TITLE_FIELD, text)) {
            CharTermAttribute attribute = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken() && terms.size() < MAX_QUERY_TERMS) {
                terms.add(attribute.toString());
            }
            stream.end();
        }
        return new ArrayList<>(terms);
    }

    /** A keyword typed as a username: "@Minh_92" and "minh_92" are the same account. */
    private static String normalizeHandle(String value) {
        if (value == null) {
            return "";
        }
        String handle = value.trim();
        if (handle.startsWith("@")) {
            handle = handle.substring(1).trim();
        }
        return handle.toLowerCase(Locale.ROOT);
    }

    // --- following the database ----------------------------------------------------------

    @Override
    public boolean triggerReindex() {
        if (!reindexRunning.compareAndSet(false, true)) {
            log.info("Social search reindex already running; request ignored");
            return false;
        }
        try {
            reindexExecutor.execute(this::rebuildIndex);
        } catch (RuntimeException e) {
            reindexRunning.set(false);
            throw e;
        }
        return true;
    }

    @Scheduled(cron = "${goroute.search.social-index-rebuild-cron:0 40 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void nightlyRebuild() {
        triggerReindex();
    }

    /**
     * Writes every findable row with a new generation, then drops the documents of older ones:
     * whatever stopped being findable without its row changing (an account deleted under its
     * posts, a trip's stops edited) leaves the index here. Searches keep working throughout.
     */
    void rebuildIndex() {
        long started = System.currentTimeMillis();
        syncLock.lock();
        try {
            String rebuildGeneration = UUID.randomUUID().toString();
            Map<SocialSearchType, LocalDateTime> startedAt = new EnumMap<>(SocialSearchType.class);
            int total = 0;
            for (SocialSearchType kind : INDEXED_KINDS) {
                // Read before the scan: a row changed during it is caught up afterwards.
                startedAt.put(kind, maxUpdatedAt(kind).map(at -> at.minus(CATCH_UP_OVERLAP)).orElse(BEGINNING));
                total += writeAll(kind, rebuildGeneration);
            }
            writer.deleteDocuments(new BooleanQuery.Builder()
                    .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(GENERATION_FIELD, rebuildGeneration)), BooleanClause.Occur.MUST_NOT)
                    .build());
            writer.commit();
            searcherManager.maybeRefreshBlocking();
            generation = rebuildGeneration;
            changedUntil.putAll(startedAt);
            log.info("Social search reindexed {} documents in {} ms", total, System.currentTimeMillis() - started);
        } catch (Exception e) {
            // The documents already rewritten are current; the older ones stay until a rebuild
            // gets through. No rollback: it would close the writer.
            log.error("Social search reindex failed; the previous index stays in use", e);
        } finally {
            syncLock.unlock();
            reindexRunning.set(false);
        }
    }

    private int writeAll(SocialSearchType kind, String rebuildGeneration) throws IOException {
        int total = 0;
        UUID afterId = null;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Social search reindex interrupted");
            }
            List<SocialSearchIndexRow> batch = page(kind, afterId);
            for (SocialSearchIndexRow row : batch) {
                writer.updateDocument(new Term(KEY_FIELD, key(kind, row.getId())),
                        toDocument(kind, row, rebuildGeneration));
            }
            total += batch.size();
            if (batch.size() < BATCH_SIZE) {
                return total;
            }
            afterId = batch.get(batch.size() - 1).getId();
        }
    }

    /**
     * Applies the rows changed since the last pass: a findable row is (re)written, any other
     * leaves the index. Skipped while a rebuild runs and before the first one has finished.
     */
    @Scheduled(fixedDelayString = "${goroute.search.social-index-sync-ms:30000}",
            initialDelayString = "${goroute.search.social-index-sync-ms:30000}")
    public void catchUp() {
        if (writer == null || !writer.isOpen() || !syncLock.tryLock()) {
            return;
        }
        try {
            if (changedUntil.isEmpty()) {
                return;
            }
            boolean changed = false;
            for (SocialSearchType kind : INDEXED_KINDS) {
                changed |= catchUp(kind);
            }
            if (changed) {
                writer.commit();
                searcherManager.maybeRefresh();
            }
        } catch (Exception e) {
            log.warn("Social search catch-up failed; the next pass retries: {}", e.getMessage(), e);
        } finally {
            syncLock.unlock();
        }
    }

    private boolean catchUp(SocialSearchType kind) throws IOException {
        LocalDateTime from = changedUntil.get(kind);
        LocalDateTime cursorAt = from;
        UUID cursorId = MIN_UUID;
        LocalDateTime newest = null;
        boolean changed = false;
        for (int pages = 0; pages < MAX_CATCH_UP_PAGES; pages++) {
            List<SocialSearchIndexRow> batch = changes(kind, cursorAt, cursorId);
            for (SocialSearchIndexRow row : batch) {
                Term key = new Term(KEY_FIELD, key(kind, row.getId()));
                if (Boolean.TRUE.equals(row.getEligible())) {
                    writer.updateDocument(key, toDocument(kind, row, generation));
                } else {
                    writer.deleteDocuments(key);
                }
                changed = true;
            }
            if (!batch.isEmpty()) {
                SocialSearchIndexRow last = batch.get(batch.size() - 1);
                cursorAt = last.getUpdatedAt();
                cursorId = last.getId();
                newest = last.getUpdatedAt();
            }
            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }
        if (newest != null) {
            LocalDateTime next = newest.minus(CATCH_UP_OVERLAP);
            if (next.isAfter(from)) {
                changedUntil.put(kind, next);
            }
        }
        return changed;
    }

    private List<SocialSearchIndexRow> page(SocialSearchType kind, UUID afterId) {
        return switch (kind) {
            case USER -> userRepository.findSearchIndexPage(afterId, BATCH_SIZE);
            case CHECKIN -> checkinRepository.findSearchIndexPage(afterId, BATCH_SIZE);
            case TRIP -> tripRepository.findSearchIndexPage(afterId, BATCH_SIZE);
            case ALL -> List.of();
        };
    }

    private List<SocialSearchIndexRow> changes(SocialSearchType kind, LocalDateTime afterUpdatedAt, UUID afterId) {
        return switch (kind) {
            case USER -> userRepository.findSearchIndexChanges(afterUpdatedAt, afterId, BATCH_SIZE);
            case CHECKIN -> checkinRepository.findSearchIndexChanges(afterUpdatedAt, afterId, BATCH_SIZE);
            case TRIP -> tripRepository.findSearchIndexChanges(afterUpdatedAt, afterId, BATCH_SIZE);
            case ALL -> List.of();
        };
    }

    private Optional<LocalDateTime> maxUpdatedAt(SocialSearchType kind) {
        return switch (kind) {
            case USER -> userRepository.findSearchIndexMaxUpdatedAt();
            case CHECKIN -> checkinRepository.findSearchIndexMaxUpdatedAt();
            case TRIP -> tripRepository.findSearchIndexMaxUpdatedAt();
            case ALL -> Optional.empty();
        };
    }

    private static Document toDocument(SocialSearchType kind, SocialSearchIndexRow row, String generation) {
        Document doc = new Document();
        doc.add(new StringField(KEY_FIELD, key(kind, row.getId()), Field.Store.NO));
        doc.add(new StringField(TYPE_FIELD, kind.name(), Field.Store.YES));
        doc.add(new StoredField(ID_FIELD, row.getId().toString()));
        doc.add(new StringField(GENERATION_FIELD, generation, Field.Store.NO));
        addText(doc, TITLE_FIELD, row.getTitle());
        addText(doc, BODY_FIELD, truncate(row.getBody()));
        String handle = normalizeHandle(row.getHandle());
        if (!handle.isEmpty()) {
            addText(doc, HANDLE_FIELD, handle);
            doc.add(new StringField(HANDLE_EXACT_FIELD, handle, Field.Store.NO));
        }
        long popularity = row.getPopularity() == null ? 0L : Math.max(0L, row.getPopularity());
        doc.add(new FeatureField(FEATURES_FIELD, POPULARITY_FEATURE, 1f + Math.min(popularity, 1_000_000L)));
        if (row.getPublishedAt() != null) {
            doc.add(new LongField(PUBLISHED_FIELD, row.getPublishedAt().toInstant(ZoneOffset.UTC).toEpochMilli()));
        }
        return doc;
    }

    private static void addText(Document doc, String field, String value) {
        if (value != null && !value.isBlank()) {
            doc.add(new TextField(field, value.trim(), Field.Store.NO));
        }
    }

    private static String truncate(String body) {
        return body == null || body.length() <= MAX_BODY_CHARS ? body : body.substring(0, MAX_BODY_CHARS);
    }

    private static String key(SocialSearchType kind, UUID id) {
        return kind.name() + ":" + id;
    }
}

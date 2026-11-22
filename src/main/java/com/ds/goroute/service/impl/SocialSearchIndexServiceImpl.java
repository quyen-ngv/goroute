package com.ds.goroute.service.impl;

import com.ds.goroute.dto.SocialSearchHits;
import com.ds.goroute.entity.SocialSearchIndexRow;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.repository.ActivityBookingRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.SocialSearchIndexService;
import com.ds.goroute.type.SocialSearchType;
import com.ds.goroute.utils.LuceneKeywordQuery;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
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
    /** A tour's source (CATALOGUE or MARKETPLACE); other kinds have none. */
    private static final String VARIANT_FIELD = "variant";

    // Text: a match in the name outweighs one in the caption; a whole username wins outright.
    private static final float HANDLE_EXACT_BOOST = 20f;
    private static final LuceneKeywordQuery.Field TITLE =
            new LuceneKeywordQuery.Field(TITLE_FIELD, 4f, 2f, 1f, 6f, 1, 4);
    private static final LuceneKeywordQuery.Field HANDLE =
            new LuceneKeywordQuery.Field(HANDLE_FIELD, 5f, 3f, 1f, 0f, 1, 4);
    private static final LuceneKeywordQuery.Field BODY =
            new LuceneKeywordQuery.Field(BODY_FIELD, 1.5f, 0.5f, 0.3f, 2f, 3, 5);
    private static final List<LuceneKeywordQuery.Field> ALL_FIELDS = List.of(TITLE, HANDLE, BODY);
    /** Name and username only: what the All tab's people, tours and events sections must match. */
    private static final List<LuceneKeywordQuery.Field> NAME_FIELDS = List.of(TITLE, HANDLE);
    // Signals: each adds less than 1, so they order equal matches and never beat a better one.
    private static final float POPULARITY_WEIGHT = 1f;
    private static final float POPULARITY_PIVOT = 10f;
    private static final float FRESHNESS_WEIGHT = 1f;
    private static final long FRESHNESS_PIVOT_MILLIS = TimeUnit.DAYS.toMillis(30);

    private static final List<SocialSearchType> INDEXED_KINDS =
            List.of(SocialSearchType.USER, SocialSearchType.CHECKIN, SocialSearchType.TRIP,
                    SocialSearchType.TOUR, SocialSearchType.EVENT);

    private final UserRepository userRepository;
    private final UserCheckinRepository checkinRepository;
    private final TripRepository tripRepository;
    private final ActivityBookingRepository activityBookingRepository;
    private final LocalEventRepository localEventRepository;

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
    /**
     * Guarded by {@link #syncLock}. Set only while a kind has more changes than one pass reads:
     * the id of the last row read at {@link #changedUntil}, so the next pass resumes after it
     * instead of re-reading the same rows (more than a pass's worth inside the overlap window
     * would otherwise never let the start move).
     */
    private final Map<SocialSearchType, UUID> resumeAfterId = new EnumMap<>(SocialSearchType.class);
    /** Guarded by {@link #syncLock}: the generation catch-up writes stamp. */
    private String generation = UUID.randomUUID().toString();

    @PostConstruct
    public void init() throws IOException {
        analyzer = LuceneKeywordQuery.createAnalyzer();
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
                                   String tourVariant, boolean namesOnly, int offset, int limit) throws IOException {
        if (kinds == null || kinds.isEmpty() || offset < 0 || limit < 1 || offset >= MAX_RESULT_WINDOW) {
            return SocialSearchHits.EMPTY;
        }
        List<LuceneKeywordQuery.Word> words = LuceneKeywordQuery.words(keyword, MAX_QUERY_TERMS);
        if (words.isEmpty()) {
            return SocialSearchHits.EMPTY;
        }
        IndexSearcher searcher = searcherManager.acquire();
        try {
            ScoreDoc[] relevant = relevant(searcher, words, keyword, kinds, excludeUserId, tourVariant,
                    namesOnly ? NAME_FIELDS : ALL_FIELDS);
            StoredFields stored = searcher.storedFields();
            int end = Math.min(relevant.length, offset + limit);
            List<SocialSearchHits.Hit> hits = new ArrayList<>(Math.max(0, end - offset));
            for (int index = offset; index < end; index++) {
                Document doc = stored.document(relevant[index].doc, Set.of(TYPE_FIELD, ID_FIELD));
                hits.add(new SocialSearchHits.Hit(
                        SocialSearchType.valueOf(doc.get(TYPE_FIELD)), UUID.fromString(doc.get(ID_FIELD))));
            }
            return new SocialSearchHits(hits, relevant.length);
        } finally {
            searcherManager.release(searcher);
        }
    }

    @Override
    public Map<SocialSearchType, Integer> countByKind(String keyword, UUID excludeUserId, String tourVariant)
            throws IOException {
        Map<SocialSearchType, Integer> counts = new LinkedHashMap<>();
        INDEXED_KINDS.forEach(kind -> counts.put(kind, 0));
        List<LuceneKeywordQuery.Word> words = LuceneKeywordQuery.words(keyword, MAX_QUERY_TERMS);
        if (words.isEmpty()) {
            return counts;
        }
        IndexSearcher searcher = searcherManager.acquire();
        try {
            // Each kind's tab lists what its own search keeps, so each is counted that same way.
            for (SocialSearchType kind : INDEXED_KINDS) {
                counts.put(kind, relevant(searcher, words, keyword, Set.of(kind), excludeUserId, tourVariant,
                        ALL_FIELDS).length);
            }
            return counts;
        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * The hits worth showing, best first, at most {@link #MAX_RESULT_WINDOW}: those of the
     * strictest tier that finds anything ({@link LuceneKeywordQuery#STRICT_TIERS}: typos only
     * when the words as typed match nothing), less that tier's weak tail
     * ({@link LuceneKeywordQuery#relevant}).
     */
    private static ScoreDoc[] relevant(IndexSearcher searcher, List<LuceneKeywordQuery.Word> words, String keyword,
                                       Set<SocialSearchType> kinds, UUID excludeUserId, String tourVariant,
                                       List<LuceneKeywordQuery.Field> fields) throws IOException {
        for (LuceneKeywordQuery.Match match : LuceneKeywordQuery.STRICT_TIERS) {
            Query query = withRanking(filtered(textQuery(words, keyword, fields, match), kinds, excludeUserId,
                    tourVariant));
            ScoreDoc[] hits = searcher.search(query, MAX_RESULT_WINDOW).scoreDocs;
            if (hits.length > 0) {
                return LuceneKeywordQuery.relevant(hits);
            }
        }
        return new ScoreDoc[0];
    }

    /**
     * The keyword's words in {@code fields} ({@link LuceneKeywordQuery}: every word, the last
     * also as a start, accents counting extra), plus a large bonus for a whole username when
     * the username is searched.
     */
    private static Query textQuery(List<LuceneKeywordQuery.Word> words, String keyword,
                                   List<LuceneKeywordQuery.Field> fields, LuceneKeywordQuery.Match match) {
        BooleanQuery.Builder query = new BooleanQuery.Builder()
                .add(LuceneKeywordQuery.build(words, fields, match), BooleanClause.Occur.MUST);
        String handle = normalizeHandle(keyword);
        if (!handle.isEmpty() && fields.contains(HANDLE)) {
            query.add(new BoostQuery(new TermQuery(new Term(HANDLE_EXACT_FIELD, handle)), HANDLE_EXACT_BOOST),
                    BooleanClause.Occur.SHOULD);
        }
        return query.build();
    }

    private static Query filtered(Query text, Set<SocialSearchType> kinds, UUID excludeUserId, String tourVariant) {
        BooleanQuery.Builder query = new BooleanQuery.Builder()
                .add(text, BooleanClause.Occur.MUST)
                .add(new TermInSetQuery(TYPE_FIELD, kinds.stream().map(kind -> new BytesRef(kind.name())).toList()),
                        BooleanClause.Occur.FILTER);
        if (excludeUserId != null) {
            query.add(new TermQuery(new Term(KEY_FIELD, key(SocialSearchType.USER, excludeUserId))),
                    BooleanClause.Occur.MUST_NOT);
        }
        if (tourVariant != null && kinds.contains(SocialSearchType.TOUR)) {
            // Tours of the other source are left out; every other kind passes.
            query.add(new BooleanQuery.Builder()
                            .add(new TermQuery(new Term(VARIANT_FIELD, tourVariant)), BooleanClause.Occur.SHOULD)
                            .add(new BooleanQuery.Builder()
                                    .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                                    .add(new TermQuery(new Term(TYPE_FIELD, SocialSearchType.TOUR.name())),
                                            BooleanClause.Occur.MUST_NOT)
                                    .build(), BooleanClause.Occur.SHOULD)
                            .build(),
                    BooleanClause.Occur.FILTER);
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
            resumeAfterId.clear();
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
        UUID resumeId = resumeAfterId.get(kind);
        LocalDateTime cursorAt = from;
        UUID cursorId = resumeId != null ? resumeId : MIN_UUID;
        LocalDateTime newest = null;
        boolean changed = false;
        boolean drained = false;
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
                drained = true;
                break;
            }
        }
        if (!drained) {
            // A backlog bigger than one pass (a bulk update): resume exactly after the last row
            // read, with no overlap, until it is drained.
            changedUntil.put(kind, cursorAt);
            resumeAfterId.put(kind, cursorId);
        } else if (newest != null) {
            LocalDateTime next = newest.minus(CATCH_UP_OVERLAP);
            changedUntil.put(kind, next.isAfter(from) ? next : from);
            resumeAfterId.remove(kind);
        } else if (resumeId != null) {
            // The backlog ended exactly on a pass boundary: go back to reading with the overlap,
            // which also picks up rows committed late while it was being drained.
            changedUntil.put(kind, from.minus(CATCH_UP_OVERLAP));
            resumeAfterId.remove(kind);
        }
        return changed;
    }

    private List<SocialSearchIndexRow> page(SocialSearchType kind, UUID afterId) {
        return switch (kind) {
            case USER -> userRepository.findSearchIndexPage(afterId, BATCH_SIZE);
            case CHECKIN -> checkinRepository.findSearchIndexPage(afterId, BATCH_SIZE);
            case TRIP -> tripRepository.findSearchIndexPage(afterId, BATCH_SIZE);
            case TOUR -> activityBookingRepository.findSearchIndexPage(afterId, BATCH_SIZE);
            case EVENT -> localEventRepository.findSearchIndexPage(afterId, BATCH_SIZE);
            case ALL, PLACE, STAY -> List.of();
        };
    }

    private List<SocialSearchIndexRow> changes(SocialSearchType kind, LocalDateTime afterUpdatedAt, UUID afterId) {
        return switch (kind) {
            case USER -> userRepository.findSearchIndexChanges(afterUpdatedAt, afterId, BATCH_SIZE);
            case CHECKIN -> checkinRepository.findSearchIndexChanges(afterUpdatedAt, afterId, BATCH_SIZE);
            case TRIP -> tripRepository.findSearchIndexChanges(afterUpdatedAt, afterId, BATCH_SIZE);
            case TOUR -> activityBookingRepository.findSearchIndexChanges(afterUpdatedAt, afterId, BATCH_SIZE);
            case EVENT -> localEventRepository.findSearchIndexChanges(afterUpdatedAt, afterId, BATCH_SIZE);
            case ALL, PLACE, STAY -> List.of();
        };
    }

    private Optional<LocalDateTime> maxUpdatedAt(SocialSearchType kind) {
        return switch (kind) {
            case USER -> userRepository.findSearchIndexMaxUpdatedAt();
            case CHECKIN -> checkinRepository.findSearchIndexMaxUpdatedAt();
            case TRIP -> tripRepository.findSearchIndexMaxUpdatedAt();
            case TOUR -> activityBookingRepository.findSearchIndexMaxUpdatedAt();
            case EVENT -> localEventRepository.findSearchIndexMaxUpdatedAt();
            case ALL, PLACE, STAY -> Optional.empty();
        };
    }

    private static Document toDocument(SocialSearchType kind, SocialSearchIndexRow row, String generation) {
        Document doc = new Document();
        doc.add(new StringField(KEY_FIELD, key(kind, row.getId()), Field.Store.NO));
        doc.add(new StringField(TYPE_FIELD, kind.name(), Field.Store.YES));
        doc.add(new StoredField(ID_FIELD, row.getId().toString()));
        doc.add(new StringField(GENERATION_FIELD, generation, Field.Store.NO));
        if (row.getVariant() != null && !row.getVariant().isBlank()) {
            doc.add(new StringField(VARIANT_FIELD, row.getVariant(), Field.Store.NO));
        }
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
            doc.add(new TextField(field, LuceneKeywordQuery.normalize(value.trim()), Field.Store.NO));
        }
    }

    private static String truncate(String body) {
        return body == null || body.length() <= MAX_BODY_CHARS ? body : body.substring(0, MAX_BODY_CHARS);
    }

    private static String key(SocialSearchType kind, UUID id) {
        return kind.name() + ":" + id;
    }
}

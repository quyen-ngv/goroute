package com.ds.goroute.feed.index;

import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedKey;
import com.ds.goroute.mapper.FeedItemMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A Lucene copy of feed_items for candidate selection (.docs/FEED_NEARBY_TIME_PLAN_2026-11-04.md
 * §6.3, step 3). Holds only what candidate plans filter and sort on; the rows themselves are
 * read back from Postgres, so the index can lag a moment without serving stale data.
 *
 * <p>Sorted by published_at, newest first, so a "newest k" read stops after k matches. Kept in
 * step by {@link FeedIndexService} after every batch it writes to feed_items, rebuilt in full at
 * start-up and every night (whatever a missed update left behind goes then). Until the first
 * rebuild finishes the index is not {@link #ready()} and candidates come from SQL.
 */
@Component
@Slf4j
public class FeedLuceneIndex {

    /** Part of the directory name: a new layout gets a fresh directory and a rebuild. */
    static final int SCHEMA = 1;
    static final int PAGE = 5_000;

    public static final String KEY = "key";
    public static final String TYPE = "type";
    public static final String AUTHOR = "author";
    public static final String PROVINCE = "province";
    public static final String FLAGS = "flags";
    public static final String PUBLISHED = "published";
    public static final String IMPRESSIONS = "impressions";
    public static final String ENGAGEMENT = "engagement";
    public static final String POINT = "point";
    static final String GENERATION = "generation";

    /** {@link #FLAGS} values. */
    public static final String ELIGIBLE = "eligible";
    public static final String RECOMMEND = "recommend";
    /** The fill ladder's relaxed gate: recommended, not a trip, a photo, or two stops. */
    public static final String RELAXED = "relaxed";

    private final FeedItemMapper itemMapper;
    private final String indexPath;

    private Directory directory;
    private IndexWriter writer;
    private SearcherManager searcherManager;
    private ExecutorService rebuildExecutor;

    private final AtomicBoolean rebuilding = new AtomicBoolean();
    private volatile boolean ready;
    /** The generation documents are written with; a rebuild drops every other one. */
    private volatile String generation = UUID.randomUUID().toString();
    /**
     * A refresh writes each document under the read lock with the generation current at that
     * moment; a rebuild switches the generation under the write lock. So no refresh write can
     * land with the old generation after the switch, where the rebuild's last step would drop it.
     */
    private final ReadWriteLock generationLock = new ReentrantReadWriteLock();

    public FeedLuceneIndex(FeedItemMapper itemMapper,
                           @Value("${goroute.feed.index-path:data/lucene-index/feed}") String indexPath) {
        this.itemMapper = itemMapper;
        this.indexPath = indexPath;
    }

    @PostConstruct
    public void init() throws IOException {
        Path dir = Paths.get(indexPath, "v" + SCHEMA);
        Files.createDirectories(dir);
        directory = FSDirectory.open(dir);
        IndexWriterConfig config = new IndexWriterConfig();
        config.setIndexSort(new Sort(new SortField(PUBLISHED, SortField.Type.LONG, true)));
        writer = new IndexWriter(directory, config);
        searcherManager = new SearcherManager(writer, new SearcherFactory());
        rebuildExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "feed-index-rebuild");
            thread.setDaemon(true);
            return thread;
        });
        log.info("Feed Lucene index at {}", dir.toAbsolutePath());
        triggerRebuild();
    }

    @PreDestroy
    public void close() throws IOException {
        ready = false;
        if (rebuildExecutor != null) {
            rebuildExecutor.shutdownNow();
            try {
                rebuildExecutor.awaitTermination(10, TimeUnit.SECONDS);
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
    }

    /** True once a full rebuild has gone through since start-up. */
    public boolean ready() {
        return ready && writer != null && writer.isOpen();
    }

    public boolean triggerRebuild() {
        if (!rebuilding.compareAndSet(false, true)) {
            return false;
        }
        try {
            rebuildExecutor.execute(this::rebuild);
        } catch (RuntimeException e) {
            rebuilding.set(false);
            throw e;
        }
        return true;
    }

    @Scheduled(cron = "${goroute.jobs.feed-lucene-rebuild-cron:0 50 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void nightlyRebuild() {
        triggerRebuild();
    }

    /** Every row of feed_items with a new generation, then the older generations go. */
    void rebuild() {
        long started = System.currentTimeMillis();
        try {
            String rebuildGeneration = UUID.randomUUID().toString();
            // From here on, updates from FeedIndexService are written with the new generation too.
            generationLock.writeLock().lock();
            try {
                generation = rebuildGeneration;
            } finally {
                generationLock.writeLock().unlock();
            }
            int total = 0;
            String afterType = "";
            UUID afterId = new UUID(0, 0);
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Feed index rebuild interrupted");
                }
                List<FeedItemRow> page = itemMapper.selectPage(afterType, afterId, PAGE);
                for (FeedItemRow row : page) {
                    writer.updateDocument(new Term(KEY, key(row)), document(row, rebuildGeneration));
                }
                total += page.size();
                if (page.size() < PAGE) {
                    break;
                }
                FeedItemRow last = page.get(page.size() - 1);
                afterType = last.getItemType();
                afterId = last.getItemId();
            }
            writer.deleteDocuments(new BooleanQuery.Builder()
                    .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(GENERATION, rebuildGeneration)), BooleanClause.Occur.MUST_NOT)
                    .build());
            writer.commit();
            searcherManager.maybeRefreshBlocking();
            ready = true;
            log.info("Feed Lucene index rebuilt: {} posts in {} ms", total, System.currentTimeMillis() - started);
        } catch (Exception e) {
            log.error("Feed Lucene index rebuild failed; candidates keep coming from {}",
                    ready ? "the previous index" : "SQL", e);
        } finally {
            rebuilding.set(false);
        }
    }

    /**
     * Rewrites the documents of {@code keys} from feed_items as it is now; a key without a row
     * leaves the index. Called after every batch FeedIndexService writes.
     */
    public void refresh(Collection<FeedKey> keys) {
        if (writer == null || !writer.isOpen() || keys == null || keys.isEmpty()) {
            return;
        }
        try {
            List<FeedKey> all = new ArrayList<>(keys);
            for (int from = 0; from < all.size(); from += 1000) {
                List<FeedKey> slice = all.subList(from, Math.min(from + 1000, all.size()));
                Set<String> found = new HashSet<>();
                for (FeedItemRow row : itemMapper.selectByKeys(slice)) {
                    String key = key(row);
                    found.add(key);
                    generationLock.readLock().lock();
                    try {
                        writer.updateDocument(new Term(KEY, key), document(row, generation));
                    } finally {
                        generationLock.readLock().unlock();
                    }
                }
                for (FeedKey key : slice) {
                    if (!found.contains(key.key())) {
                        writer.deleteDocuments(new Term(KEY, key.key()));
                    }
                }
            }
            searcherManager.maybeRefresh();
        } catch (IOException | RuntimeException e) {
            // The nightly rebuild repairs what this missed; Postgres re-checks every candidate.
            log.warn("Feed Lucene index update of {} posts failed: {}", keys.size(), e.getMessage());
        }
    }

    /** The keys of the top {@code n} documents for {@code query} in {@code sort} order. */
    public List<FeedKey> search(Query query, Sort sort, int n) {
        IndexSearcher searcher;
        try {
            searcher = searcherManager.acquire();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            TopFieldDocs top = searcher.search(query, n, sort, false);
            StoredFields stored = searcher.storedFields();
            List<FeedKey> keys = new ArrayList<>(top.scoreDocs.length);
            for (ScoreDoc hit : top.scoreDocs) {
                keys.add(parse(stored.document(hit.doc).get(KEY)));
            }
            return keys;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                searcherManager.release(searcher);
            } catch (IOException e) {
                log.debug("Feed index searcher release failed: {}", e.getMessage());
            }
        }
    }

    /** published_at as indexed: the same fixed offset on both sides, so ranges compare right. */
    public static long millis(LocalDateTime at) {
        return at.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    static Document document(FeedItemRow row, String generation) {
        Document doc = new Document();
        doc.add(new StringField(KEY, key(row), Field.Store.YES));
        doc.add(new StringField(TYPE, row.getItemType(), Field.Store.NO));
        doc.add(new StringField(AUTHOR, row.getAuthorId().toString(), Field.Store.NO));
        if (row.getProvinceCode() != null) {
            doc.add(new StringField(PROVINCE, row.getProvinceCode(), Field.Store.NO));
        }
        if (row.isEligible()) {
            doc.add(new StringField(FLAGS, ELIGIBLE, Field.Store.NO));
        }
        if (row.isRecommendOk()) {
            doc.add(new StringField(FLAGS, RECOMMEND, Field.Store.NO));
        }
        if (row.isRecommendOk() || !row.isTrip() || row.isHasPhoto() || row.getStopCount() >= 2) {
            doc.add(new StringField(FLAGS, RELAXED, Field.Store.NO));
        }
        long published = millis(row.getPublishedAt());
        doc.add(new LongPoint(PUBLISHED, published));
        doc.add(new NumericDocValuesField(PUBLISHED, published));
        doc.add(new IntPoint(IMPRESSIONS, row.getImpressionCount()));
        doc.add(new FloatDocValuesField(ENGAGEMENT, row.getEngagementScore()));
        if (row.getLatitude() != null && row.getLongitude() != null) {
            doc.add(new LatLonPoint(POINT, row.getLatitude(), row.getLongitude()));
        }
        doc.add(new StringField(GENERATION, generation, Field.Store.NO));
        return doc;
    }

    private static String key(FeedItemRow row) {
        return row.getItemType() + ':' + row.getItemId();
    }

    private static FeedKey parse(String key) {
        int colon = key.indexOf(':');
        return new FeedKey(key.substring(0, colon), UUID.fromString(key.substring(colon + 1)));
    }
}

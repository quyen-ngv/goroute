package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.entity.FoodTagRow;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.entity.Ward;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PlaceTranslationMapper;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.WardRepository;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.ProvinceNameResolver;
import com.ds.goroute.type.PlaceBusinessStatus;
import com.ds.goroute.type.PlaceSearchSort;
import com.ds.goroute.type.PlaceSubType;
import com.ds.goroute.utils.CitySlugResolver;
import com.ds.goroute.utils.DestinationMatchUtils;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.LuceneKeywordQuery;
import com.ds.goroute.utils.MapTile;
import com.ds.goroute.utils.PlaceAddressTerms;
import com.ds.goroute.utils.PlaceCategoryTerms;
import com.ds.goroute.utils.PlaceProminence;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.IndexOrDocValuesQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceSearchIndexServiceImpl implements PlaceSearchIndexService {

    private static final int REINDEX_BATCH_SIZE = 500;
    /** Largest page the location-free name search serves (the console picker asks for 500). */
    static final int MAX_NAME_SEARCH_SIZE = 500;
    private static final String INDEX_SCHEMA_VERSION = "15";
    private static final String ID_FIELD = "id";
    private static final Set<String> ID_ONLY = Set.of(ID_FIELD);
    private static final String NAME_FIELD = "name";
    /**
     * The names' neighbouring words written together ({@link LuceneKeywordQuery#joinedWords}),
     * so "hanoi" and "hoankiem" find "Hà Nội" and "Hồ Hoàn Kiếm".
     */
    private static final String NAME_JOINED_FIELD = "name_joined";
    /**
     * How much a word weighs in a place's names (the weights a client's minLuceneScore is set
     * against). A word found only as two words joined weighs less than the word itself, and
     * joined words make no phrase.
     */
    private static final List<LuceneKeywordQuery.Field> NAME_FIELDS = List.of(
            new LuceneKeywordQuery.Field(NAME_FIELD, 4f, 2f, 0.75f, 10f, 1, 4),
            new LuceneKeywordQuery.Field(NAME_JOINED_FIELD, 3f, 1.5f, 0.5f, 0f, 4, 6));
    /**
     * Where the place is (schema 15): pairs of neighbouring words of its address and of the
     * names of its ward and province ("nha_chung", "cua_nam", "ha_noi"), and the parts that are
     * one word ({@link PlaceAddressTerms}).
     */
    private static final String ADDRESS_PAIR_FIELD = "addr_2g";
    private static final String ADDRESS_SINGLE_FIELD = "addr_1";
    private static final String HOUSE_NUMBER_FIELD = "house_no";
    /** What the place is, in words ({@link PlaceCategoryTerms}): "ca_phe", "coffee". */
    private static final String KIND_PAIR_FIELD = "kind_2g";
    private static final String KIND_SINGLE_FIELD = "kind_1";
    /** Each name whole, folded ("pho mai"): a keyword that says a whole name scores on it. */
    private static final String NAME_EXACT_FIELD = "name_exact";
    private static final int MAX_EXACT_NAME_WORDS = 12;
    /**
     * A word explained by where or what the place is weighs less than one in its name, and a
     * whole name said in the keyword lifts the place over longer names holding the same words.
     */
    private static final LuceneKeywordQuery.Extras PLACE_EXTRAS = new LuceneKeywordQuery.Extras(
            List.of(new LuceneKeywordQuery.TermField(ADDRESS_PAIR_FIELD, ADDRESS_SINGLE_FIELD, 2f, true),
                    new LuceneKeywordQuery.TermField(KIND_PAIR_FIELD, KIND_SINGLE_FIELD, 1f, false)),
            HOUSE_NUMBER_FIELD, 1f, NAME_EXACT_FIELD, 5f, PlaceAddressTerms::markers);
    /** More places than this differing from the database: rebuild instead of indexing one by one. */
    private static final int SYNC_REBUILD_THRESHOLD = 1_000;
    private static final int MAX_KEYWORD_WORDS = 8;
    /**
     * Lucene refuses a query of more leaf clauses than its limit (1,024). A place keyword of six
     * words read in every spelling, over the name, address and kind fields, with typos, passes it.
     */
    private static final int MAX_QUERY_CLAUSES = 16_384;
    private static final String LOCATION_FIELD = "location";
    private static final String VISIBILITY_FIELD = "visibility";
    /**
     * "1" for a permanently closed place (PlaceBusinessStatus, schema 14): it still answers a
     * name search, but map tiles and the popularity sort leave it out.
     */
    private static final String CLOSED_FIELD = "closed";
    private static final Query PERMANENTLY_CLOSED = new TermQuery(new Term(CLOSED_FIELD, "1"));
    private static final String CATEGORY_FIELD = "category";
    private static final String PLACE_GROUP_FIELD = "place_group";
    private static final String SUB_TYPE_FIELD = "sub_type";
    private static final String RATING_FIELD = "review_rating";
    private static final String EFFECTIVE_RATING_SORT_FIELD = "effective_rating_sort";
    /** {@link PlaceProminence}: which places a zoomed-out map keeps. */
    private static final String PROMINENCE_FIELD = "prominence";
    /**
     * A map tile keeps its most prominent place in each tile this many zooms closer: 4 x 4
     * cells, so places spread over the tile instead of piling up in its busiest corner.
     */
    static final int MAP_TILE_CELL_DEPTH = 2;
    private static final String CITY_SLUG_FIELD = "city_slug";
    private static final String FOOD_ID_FIELD = "food_id";
    private static final String HAS_LINKED_FOOD_FIELD = "has_linked_food";
    /** Marks the documents of one rebuild, so the swap can drop every older one after adding them. */
    private static final String REBUILD_GENERATION_FIELD = "rebuild_generation";
    private static final float DISTANCE_SCORE_WEIGHT = 1.5f;
    private static final double MIN_DISTANCE_PIVOT_METERS = 100.0;
    private static final double MAX_DISTANCE_PIVOT_METERS = 5_000.0;
    /** A name search leans towards its point at the scale of a region, not a street. */
    private static final double NAME_SEARCH_PIVOT_METERS = 50_000.0;
    private static final Sort EFFECTIVE_RATING_DESCENDING_SORT = new Sort(
            new SortField(EFFECTIVE_RATING_SORT_FIELD, SortField.Type.DOUBLE, true));
    private static final Sort PROMINENCE_DESCENDING_SORT = new Sort(
            new SortField(PROMINENCE_FIELD, SortField.Type.DOUBLE, true));
    /** Best name match first; equal matches (one chain's branches) the most prominent first. */
    private static final Sort SCORE_THEN_PROMINENCE_SORT = new Sort(
            SortField.FIELD_SCORE, new SortField(PROMINENCE_FIELD, SortField.Type.DOUBLE, true));

    private final PlaceRepository placeRepository;
    private final PlaceTranslationMapper placeTranslationMapper;
    private final FoodRepository foodRepository;
    private final WardRepository wardRepository;
    private final ProvinceNameResolver provinceNameResolver;

    @Value("${goroute.search.place-index-path:data/lucene-index/places}")
    private String indexPath;

    private Directory directory;
    private IndexWriter writer;
    private SearcherManager searcherManager;
    private Analyzer analyzer;
    private ExecutorService reindexExecutor;

    /**
     * Held around every writer mutation, commit and searcher refresh, so the rebuild swap
     * (delete all, add the rebuilt segments, replay) is never visible half done.
     */
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicBoolean reindexRunning = new AtomicBoolean();
    /** Places written or deleted while a rebuild runs; replayed onto the rebuilt index. */
    private final Set<UUID> changedDuringReindex = ConcurrentHashMap.newKeySet();
    /**
     * Place id → hash of its searchable names in the database ({@code findSearchNameHashes})
     * when the index last read them; null until the first rebuild or sync. What
     * {@link #syncNamesWithDatabase} compares the database against.
     */
    private volatile Map<UUID, String> indexedNameHashes;

    /**
     * Exists on disk while the live index holds writes not yet committed. Commits are batched
     * (see {@link #commitPendingChanges}), so a killed process loses the writes since the last
     * one; finding this file at startup means that happened, and the index is rebuilt. A
     * watermark on places.updated_at could not stand in for it: places are hard-deleted, and
     * translations and food tags, both indexed, change without touching the places row.
     */
    private Path uncommittedMarker;
    /** Guarded by {@link #writeLock}: the marker file is known to exist. */
    private boolean uncommittedMarked;
    /**
     * Guarded by {@link #writeLock}: writes were lost before this start, so the marker stays
     * until a rebuild has been swapped in, even across the periodic commits in between.
     */
    private boolean recoveryPending;

    @PostConstruct
    public void init() throws IOException {
        analyzer = LuceneKeywordQuery.createAnalyzer();
        if (IndexSearcher.getMaxClauseCount() < MAX_QUERY_CLAUSES) {
            IndexSearcher.setMaxClauseCount(MAX_QUERY_CLAUSES);
        }
        Path indexDir = Paths.get(indexPath);
        Files.createDirectories(indexDir);
        directory = FSDirectory.open(indexDir);
        uncommittedMarker = Paths.get(indexPath + ".uncommitted");
        uncommittedMarked = Files.exists(uncommittedMarker);
        recoveryPending = uncommittedMarked;
        writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
        searcherManager = new SearcherManager(writer, new SearcherFactory());
        reindexExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "place-search-reindex");
            thread.setDaemon(true);
            return thread;
        });
        log.info("Place Lucene index initialized at: {}", indexDir.toAbsolutePath());
        ensureSearchIndexPopulated();
    }

    /**
     * Rebuilds the index in the background at every start, while the index already on disk
     * keeps serving searches. The index lives on a volume and outlives deploys, but the
     * database changes behind the app's back (a migration rewriting an indexed column, a
     * manual fix): when every deploy started from an empty index those changes were picked
     * up for free, and searches were empty until the rebuild finished.
     */
    private void ensureSearchIndexPopulated() {
        try {
            IndexSearcher searcher = searcherManager.acquire();
            try {
                int docCount = searcher.getIndexReader().numDocs();
                if (docCount == 0 && !recoveryPending && placeRepository.countAll() == 0) {
                    return;
                }
                if (recoveryPending) {
                    log.warn("Place Lucene index lost writes that were never committed (the previous run "
                            + "did not stop cleanly); reindexing");
                } else if (docCount == 0) {
                    log.warn("Place Lucene index is empty but DB has data — reindexing");
                } else if (!isCurrentSchema(searcher)) {
                    log.warn("Place Lucene index schema is outdated; reindexing");
                } else {
                    log.info("Place Lucene index has {} places; refreshing it from the database in the background",
                            docCount);
                }
                triggerReindex();
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            log.warn("Could not verify place Lucene index: {}", e.getMessage(), e);
        }
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
        commitPendingChanges();
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

    @Override
    public void indexPlace(Place place) {
        if (place == null || place.getId() == null) {
            return;
        }
        try {
            Document document = toDocument(
                    place, placeTranslationMapper.findByPlaceId(place.getId()), findFoodIds(place.getId()),
                    whereOf(place));
            writeLock.lock();
            try {
                markUncommitted();
                writer.updateDocument(new Term(ID_FIELD, place.getId().toString()), document);
                noteChangeDuringReindex(place.getId());
                refreshSearchIndex();
            } finally {
                writeLock.unlock();
            }
        } catch (Exception e) {
            log.error("Failed to index place {}", place.getId(), e);
        }
    }

    @Override
    public void deletePlace(UUID id) {
        if (id == null) {
            return;
        }
        writeLock.lock();
        try {
            markUncommitted();
            writer.deleteDocuments(new Term(ID_FIELD, id.toString()));
            noteChangeDuringReindex(id);
            refreshSearchIndex();
        } catch (IOException e) {
            log.error("Failed to delete place {} from index", id, e);
        } finally {
            writeLock.unlock();
        }
    }

    private void noteChangeDuringReindex(UUID id) {
        if (reindexRunning.get()) {
            changedDuringReindex.add(id);
        }
    }

    /**
     * Records, durably and before the write, that the live index is about to hold an
     * uncommitted change. One file create and directory sync per commit window, not per write.
     * Caller holds {@link #writeLock}. A failure is logged and the write still goes ahead.
     */
    private void markUncommitted() {
        if (uncommittedMarked) {
            return;
        }
        try {
            Files.newByteChannel(uncommittedMarker, StandardOpenOption.CREATE, StandardOpenOption.WRITE).close();
            IOUtils.fsync(uncommittedMarker.toAbsolutePath().getParent(), true);
            uncommittedMarked = true;
        } catch (IOException e) {
            log.warn("Could not record pending place index writes in {}: {}", uncommittedMarker, e.getMessage());
        }
    }

    /** After a commit: everything written so far is durable. Caller holds {@link #writeLock}. */
    private void clearUncommitted() {
        if (!uncommittedMarked || recoveryPending) {
            return;
        }
        try {
            Files.deleteIfExists(uncommittedMarker);
            uncommittedMarked = false;
        } catch (IOException e) {
            log.warn("Could not remove {}: {}", uncommittedMarker, e.getMessage());
        }
    }

    /**
     * Writes reach searches at once through the near-real-time refresh after each one; the
     * commit that makes them survive a restart runs here, on a timer, and at shutdown.
     */
    @Scheduled(fixedDelayString = "${goroute.search.place-index-commit-ms:30000}",
            initialDelayString = "${goroute.search.place-index-commit-ms:30000}")
    public void commitPendingChanges() {
        if (writer == null || !writer.isOpen()) {
            return;
        }
        writeLock.lock();
        try {
            if (writer.hasUncommittedChanges()) {
                writer.commit();
            }
            clearUncommitted();
        } catch (IOException e) {
            log.warn("Could not commit the place Lucene index: {}", e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Brings the index in line with names that reached the database without passing through
     * the application: a SQL import of translated names, a title fixed by hand, a place
     * deleted. Compares one hash per place (visibility, title, aliases, every translated name)
     * with the one the index was last built from, and indexes the places that differ; when most
     * of the catalogue differs it rebuilds instead. A place the application itself just wrote
     * differs too and is indexed once more, which costs one document. Search caches are left to
     * expire (two minutes).
     */
    @Scheduled(fixedDelayString = "${goroute.search.place-index-sync-ms:300000}",
            initialDelayString = "${goroute.search.place-index-sync-ms:300000}")
    public void syncNamesWithDatabase() {
        if (writer == null || !writer.isOpen() || reindexRunning.get()) {
            return;
        }
        try {
            Map<UUID, String> current = placeRepository.findSearchNameHashes();
            Map<UUID, String> known = indexedNameHashes;
            if (known == null) {
                indexedNameHashes = new ConcurrentHashMap<>(current);
                return;
            }
            List<UUID> changed = current.entrySet().stream()
                    .filter(entry -> !entry.getValue().equals(known.get(entry.getKey())))
                    .map(Map.Entry::getKey)
                    .toList();
            List<UUID> removed = known.keySet().stream().filter(id -> !current.containsKey(id)).toList();
            if (changed.isEmpty() && removed.isEmpty()) {
                return;
            }
            if (changed.size() > SYNC_REBUILD_THRESHOLD) {
                log.info("Place index: {} places differ from the database; rebuilding", changed.size());
                triggerReindex();
                return;
            }
            for (UUID id : changed) {
                placeRepository.findSearchIndexRow(id).ifPresent(this::indexPlace);
                known.put(id, current.get(id));
            }
            for (UUID id : removed) {
                deletePlace(id);
                known.remove(id);
            }
            log.info("Place index synced with the database: {} places indexed, {} removed",
                    changed.size(), removed.size());
        } catch (RuntimeException e) {
            log.warn("Could not sync the place index with the database: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean triggerReindex() {
        if (!reindexRunning.compareAndSet(false, true)) {
            log.info("Place reindex already running; request ignored");
            return false;
        }
        changedDuringReindex.clear();
        try {
            reindexExecutor.execute(this::rebuildIndex);
        } catch (RuntimeException e) {
            reindexRunning.set(false);
            throw e;
        }
        return true;
    }

    @Override
    public boolean isReindexRunning() {
        return reindexRunning.get();
    }

    /**
     * Builds the index into a fresh directory beside the live one, reading the database by
     * keyset pages of the indexed columns only, then swaps it in under the write lock and
     * replays the places written while it was building. A failure leaves the old index.
     */
    void rebuildIndex() {
        long started = System.currentTimeMillis();
        Path rebuildDir = Paths.get(indexPath + "-rebuild");
        String generation = UUID.randomUUID().toString();
        try {
            IOUtils.rm(rebuildDir);
            Files.createDirectories(rebuildDir);
            // Read before the places: a name changed while the rebuild reads is then newer than
            // its hash here, and the next sync indexes that place once more.
            Map<UUID, String> nameHashes = new ConcurrentHashMap<>(placeRepository.findSearchNameHashes());
            int total;
            try (Directory rebuildDirectory = FSDirectory.open(rebuildDir)) {
                try (IndexWriter rebuildWriter = new IndexWriter(rebuildDirectory, new IndexWriterConfig(analyzer))) {
                    total = writeAllPlaces(rebuildWriter, generation);
                    rebuildWriter.commit();
                }
                swapIn(rebuildDirectory, generation);
            }
            indexedNameHashes = nameHashes;
            log.info("Reindexed {} places in {} ms", total, System.currentTimeMillis() - started);
        } catch (Exception e) {
            log.error("Place reindex failed; the previous index stays in use", e);
        } finally {
            reindexRunning.set(false);
            changedDuringReindex.clear();
            try {
                IOUtils.rm(rebuildDir);
            } catch (IOException e) {
                log.warn("Could not remove the rebuild directory {}: {}", rebuildDir, e.getMessage());
            }
        }
    }

    private int writeAllPlaces(IndexWriter target, String generation) throws IOException {
        int total = 0;
        UUID afterId = null;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Place reindex interrupted");
            }
            List<Place> batch = placeRepository.findSearchIndexPage(afterId, REINDEX_BATCH_SIZE);
            if (batch.isEmpty()) {
                return total;
            }
            List<UUID> placeIds = batch.stream().map(Place::getId).toList();
            Map<UUID, List<PlaceTranslation>> translationsByPlaceId = placeTranslationMapper.findByPlaceIds(placeIds)
                    .stream()
                    .collect(Collectors.groupingBy(PlaceTranslation::getPlaceId));
            Map<UUID, Set<UUID>> foodIdsByPlaceId = groupFoodIds(foodRepository.findFoodTagsByPlaceIds(placeIds));
            Map<String, Ward> wardsByCode = findWards(batch);
            for (Place place : batch) {
                Document document = toDocument(
                        place,
                        translationsByPlaceId.getOrDefault(place.getId(), List.of()),
                        foodIdsByPlaceId.getOrDefault(place.getId(), Set.of()),
                        whereOf(place.getAddress(),
                                place.getWardCode() == null ? null : wardsByCode.get(place.getWardCode()),
                                place.getProvinceCode()));
                document.add(new StringField(REBUILD_GENERATION_FIELD, generation, Field.Store.NO));
                target.addDocument(document);
            }
            total += batch.size();
            if (batch.size() < REINDEX_BATCH_SIZE) {
                return total;
            }
            afterId = batch.get(batch.size() - 1).getId();
        }
    }

    /**
     * Adds the rebuilt documents first and only then deletes every document of an older
     * generation: a failure part way leaves the old documents searchable, never an empty
     * index.
     */
    private void swapIn(Directory rebuilt, String generation) throws IOException {
        writeLock.lock();
        try {
            writer.addIndexes(rebuilt);
            writer.deleteDocuments(new BooleanQuery.Builder()
                    .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(REBUILD_GENERATION_FIELD, generation)), BooleanClause.Occur.MUST_NOT)
                    .build());
            for (UUID id : changedDuringReindex) {
                Term idTerm = new Term(ID_FIELD, id.toString());
                Place current = placeRepository.findSearchIndexRow(id).orElse(null);
                if (current == null) {
                    writer.deleteDocuments(idTerm);
                } else {
                    writer.updateDocument(idTerm, toDocument(
                            current, placeTranslationMapper.findByPlaceId(id), findFoodIds(id), whereOfRow(current)));
                }
            }
            writer.commit();
            recoveryPending = false;
            clearUncommitted();
            searcherManager.maybeRefreshBlocking();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int countPlacesByName(String keyword, List<String> placeGroups) throws IOException {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            return relevantByName(searcher, keyword, placeGroups, false, MAX_NAME_SEARCH_SIZE).length;
        } finally {
            searcherManager.release(searcher);
        }
    }

    @Override
    public List<UUID> searchPlaceIdsByName(String keyword, List<String> placeGroups, boolean includeInactive,
                                           int page, int size) throws IOException {
        return searchPlaceIdsByName(keyword, placeGroups, includeInactive, page, size, null, null);
    }

    @Override
    public List<UUID> searchPlaceIdsByName(String keyword, List<String> placeGroups, boolean includeInactive,
                                           int page, int size, Double latitude, Double longitude)
            throws IOException {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        if (page < 0 || size < 1 || size > MAX_NAME_SEARCH_SIZE) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "page must be >= 0 and size must be between 1 and " + MAX_NAME_SEARCH_SIZE);
        }
        int offset;
        int requestedHits;
        try {
            offset = Math.multiplyExact(page, size);
            requestedHits = Math.addExact(offset, size);
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Requested page is too large");
        }
        IndexSearcher searcher = searcherManager.acquire();
        try {
            ScoreDoc[] hits = relevantByName(searcher, keyword, placeGroups, includeInactive, requestedHits,
                    latitude, longitude);
            List<UUID> ids = new ArrayList<>();
            for (int index = offset; index < hits.length; index++) {
                ids.add(UUID.fromString(searcher.doc(hits[index].doc, ID_ONLY).get(ID_FIELD)));
            }
            return ids;
        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * The first {@code maxHits} places whose name matches {@code keyword} well enough to show:
     * every word as typed, or with a typo when that finds none, less the weak tail of the best
     * match ({@link LuceneKeywordQuery}). The count and the pages of a keyword agree because
     * both come from here.
     */
    private ScoreDoc[] relevantByName(IndexSearcher searcher, String keyword, List<String> placeGroups,
                                      boolean includeInactive, int maxHits) throws IOException {
        return relevantByName(searcher, keyword, placeGroups, includeInactive, maxHits, null, null);
    }

    /**
     * {@link #relevantByName(IndexSearcher, String, List, boolean, int)}, leaning towards a point
     * when one is given: a small bonus (at most {@link #DISTANCE_SCORE_WEIGHT}, half of it at
     * {@link #NAME_SEARCH_PIVOT_METERS}) that only orders names matching alike; no place is
     * dropped for being far, and the weak-tail cut still follows the name score.
     */
    private ScoreDoc[] relevantByName(IndexSearcher searcher, String keyword, List<String> placeGroups,
                                      boolean includeInactive, int maxHits, Double latitude, Double longitude)
            throws IOException {
        List<LuceneKeywordQuery.Word> words = LuceneKeywordQuery.words(keyword, MAX_KEYWORD_WORDS);
        if (words.isEmpty()) {
            return new ScoreDoc[0];
        }
        List<String> groups = normalizeUpper(placeGroups);
        for (LuceneKeywordQuery.Match match
                : LuceneKeywordQuery.tiersFor(LuceneKeywordQuery.NAME_TIERS, words.size())) {
            BooleanQuery.Builder query = new BooleanQuery.Builder()
                    .add(placeQuery(words, match), BooleanClause.Occur.MUST);
            if (!includeInactive) {
                query.add(new TermQuery(new Term(VISIBILITY_FIELD, "ACTIVE")), BooleanClause.Occur.FILTER);
            }
            addTermSetFilter(query, PLACE_GROUP_FIELD, groups);
            if (latitude != null && longitude != null) {
                query.add(LatLonPoint.newDistanceFeatureQuery(LOCATION_FIELD, DISTANCE_SCORE_WEIGHT,
                        latitude, longitude, NAME_SEARCH_PIVOT_METERS), BooleanClause.Occur.SHOULD);
            }
            ScoreDoc[] hits = searcher.search(query.build(), maxHits).scoreDocs;
            if (hits.length > 0) {
                return withoutWeakTail(hits, match);
            }
        }
        return new ScoreDoc[0];
    }

    /**
     * The hits less the weak tail of the best one. A tier that explains every word of a longer
     * keyword ({@link LuceneKeywordQuery.Match#ALL}) has no weak tail: "bún chả hà nội" keeps
     * the bún chả places in Hà Nội under the one named "Bún Chả Hà Nội", whose whole-name bonus
     * would otherwise cut them.
     */
    private static ScoreDoc[] withoutWeakTail(ScoreDoc[] hits, LuceneKeywordQuery.Match match) {
        return match == LuceneKeywordQuery.Match.ALL ? hits : LuceneKeywordQuery.relevant(hits);
    }

    @Override
    public List<UUID> searchPlaceIds(PlaceSearchCriteria criteria) throws IOException {
        validateCriteria(criteria);
        int offset = Math.multiplyExact(criteria.page(), criteria.size());
        int requestedHits = Math.addExact(offset, criteria.size());

        IndexSearcher searcher = searcherManager.acquire();
        try {
            Query query = buildSearchQuery(searcher, criteria);
            Sort sort = switch (criteria.sort()) {
                case RATING -> EFFECTIVE_RATING_DESCENDING_SORT;
                case POPULARITY -> PROMINENCE_DESCENDING_SORT;
                case RELEVANCE -> null;
            };
            TopDocs topDocs = sort == null
                    ? searcher.search(query, requestedHits)
                    : searcher.search(query, requestedHits, sort, criteria.minLuceneScore() != null);
            List<UUID> ids = new ArrayList<>(criteria.size());
            int qualifyingHitIndex = 0;
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                if (criteria.minLuceneScore() != null && scoreDoc.score < criteria.minLuceneScore()) {
                    break;
                }
                if (qualifyingHitIndex++ < offset) {
                    continue;
                }
                // Only the id: every other stored field (names, texts in every language) is
                // read from the database for the page anyway.
                Document doc = searcher.doc(scoreDoc.doc, ID_ONLY);
                ids.add(UUID.fromString(doc.get(ID_FIELD)));
                if (ids.size() == criteria.size()) {
                    break;
                }
            }
            return ids;
        } finally {
            searcherManager.release(searcher);
        }
    }

    private Query buildSearchQuery(IndexSearcher searcher, PlaceSearchCriteria criteria) throws IOException {
        return withAreaFilters(keywordQuery(searcher, criteria), criteria);
    }

    /**
     * The keyword's query at the strictest tier ({@link LuceneKeywordQuery#AREA_TIERS}) that
     * finds a place in the area before any kind is chosen: every word as typed, then with a
     * typo, then any one word ("cafe da lat" in Da Lat, where no name says Da Lat). Chosen
     * without the kinds, so the list and its kind counts ({@link #countBySubType}) agree.
     */
    private Query keywordQuery(IndexSearcher searcher, PlaceSearchCriteria criteria) throws IOException {
        if (criteria.keyword() == null || criteria.keyword().isBlank()) {
            return new MatchAllDocsQuery();
        }
        List<LuceneKeywordQuery.Word> words = LuceneKeywordQuery.words(criteria.keyword(), MAX_KEYWORD_WORDS);
        if (words.isEmpty()) {
            return new MatchNoDocsQuery();
        }
        return placeQuery(words, areaTier(searcher, words, criteria));
    }

    /**
     * The keyword as a place search reads it: each word explained by a name of the place, by
     * where it is (street, ward, old district, province) or by what it is, so "phở mai nhà
     * chung" finds the Phở Mai on Nhà Chung street ({@link #PLACE_EXTRAS}).
     */
    private static Query placeQuery(List<LuceneKeywordQuery.Word> words, LuceneKeywordQuery.Match match) {
        return LuceneKeywordQuery.build(words, NAME_FIELDS, PLACE_EXTRAS, match);
    }

    /** The first of {@link LuceneKeywordQuery#AREA_TIERS} that finds a place in the area; the last when none does. */
    private LuceneKeywordQuery.Match areaTier(IndexSearcher searcher, List<LuceneKeywordQuery.Word> words,
                                              PlaceSearchCriteria criteria) throws IOException {
        PlaceSearchCriteria area = withoutSubTypes(criteria);
        List<LuceneKeywordQuery.Match> tiers =
                LuceneKeywordQuery.tiersFor(LuceneKeywordQuery.AREA_TIERS, words.size());
        for (LuceneKeywordQuery.Match match : tiers) {
            if (searcher.count(withAreaFilters(placeQuery(words, match), area)) > 0) {
                return match;
            }
        }
        return tiers.get(tiers.size() - 1);
    }

    @Override
    public LuceneKeywordQuery.Match areaKeywordTier(PlaceSearchCriteria criteria) throws IOException {
        validateCriteria(criteria);
        if (criteria.keyword() == null || criteria.keyword().isBlank()) {
            return null;
        }
        List<LuceneKeywordQuery.Word> words = LuceneKeywordQuery.words(criteria.keyword(), MAX_KEYWORD_WORDS);
        if (words.isEmpty()) {
            return null;
        }
        IndexSearcher searcher = searcherManager.acquire();
        try {
            return areaTier(searcher, words, criteria);
        } finally {
            searcherManager.release(searcher);
        }
    }

    @Override
    public List<UUID> searchPlaceIdsByNameOutside(String keyword, List<String> placeGroups, double latitude,
                                                  double longitude, double excludeRadiusKm, int maxHits)
            throws IOException {
        if (keyword == null || keyword.isBlank() || maxHits < 1) {
            return List.of();
        }
        List<LuceneKeywordQuery.Word> words = LuceneKeywordQuery.words(keyword, MAX_KEYWORD_WORDS);
        if (words.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(maxHits, MAX_NAME_SEARCH_SIZE);
        double radiusMeters = excludeRadiusKm * 1_000.0;
        List<String> groups = normalizeUpper(placeGroups);
        IndexSearcher searcher = searcherManager.acquire();
        try {
            for (LuceneKeywordQuery.Match match
                    : LuceneKeywordQuery.tiersFor(LuceneKeywordQuery.NAME_TIERS, words.size())) {
                BooleanQuery.Builder query = new BooleanQuery.Builder()
                        .add(placeQuery(words, match), BooleanClause.Occur.MUST)
                        .add(new TermQuery(new Term(VISIBILITY_FIELD, "ACTIVE")), BooleanClause.Occur.FILTER);
                addTermSetFilter(query, PLACE_GROUP_FIELD, groups);
                if (Double.isFinite(radiusMeters) && radiusMeters > 0) {
                    // The same circle the area search filters on, so the two never share a place.
                    query.add(new IndexOrDocValuesQuery(
                                    LatLonPoint.newDistanceQuery(LOCATION_FIELD, latitude, longitude, radiusMeters),
                                    LatLonDocValuesField.newSlowDistanceQuery(
                                            LOCATION_FIELD, latitude, longitude, radiusMeters)),
                            BooleanClause.Occur.MUST_NOT);
                }
                ScoreDoc[] hits = searcher.search(query.build(), limit, SCORE_THEN_PROMINENCE_SORT, true).scoreDocs;
                if (hits.length > 0) {
                    List<UUID> ids = new ArrayList<>(hits.length);
                    for (ScoreDoc hit : withoutWeakTail(hits, match)) {
                        ids.add(UUID.fromString(searcher.doc(hit.doc, ID_ONLY).get(ID_FIELD)));
                    }
                    return ids;
                }
            }
            return List.of();
        } finally {
            searcherManager.release(searcher);
        }
    }

    private Query withAreaFilters(Query text, PlaceSearchCriteria criteria) {
        double latitude = criteria.latitude().doubleValue();
        double longitude = criteria.longitude().doubleValue();
        double radiusMeters = criteria.radiusKm().doubleValue() * 1_000.0;
        BooleanQuery.Builder query = new BooleanQuery.Builder().add(text, BooleanClause.Occur.MUST);

        Query distanceFilter = new IndexOrDocValuesQuery(
                LatLonPoint.newDistanceQuery(LOCATION_FIELD, latitude, longitude, radiusMeters),
                LatLonDocValuesField.newSlowDistanceQuery(LOCATION_FIELD, latitude, longitude, radiusMeters));
        query.add(distanceFilter, BooleanClause.Occur.FILTER);
        double pivotMeters = Math.max(MIN_DISTANCE_PIVOT_METERS,
                Math.min(MAX_DISTANCE_PIVOT_METERS, radiusMeters / 2.0));
        query.add(LatLonPoint.newDistanceFeatureQuery(
                        LOCATION_FIELD, DISTANCE_SCORE_WEIGHT, latitude, longitude, pivotMeters),
                BooleanClause.Occur.SHOULD);

        if (!criteria.includeInactive()) {
            query.add(new TermQuery(new Term(VISIBILITY_FIELD, "ACTIVE")), BooleanClause.Occur.FILTER);
        }
        if (criteria.sort() == PlaceSearchSort.POPULARITY) {
            query.add(PERMANENTLY_CLOSED, BooleanClause.Occur.MUST_NOT);
        }
        if (criteria.category() != null && !criteria.category().isBlank()) {
            query.add(new TermQuery(new Term(CATEGORY_FIELD, normalizeLower(criteria.category()))),
                    BooleanClause.Occur.FILTER);
        }
        addTermSetFilter(query, PLACE_GROUP_FIELD, normalizeUpper(criteria.placeGroups()));
        addSubTypeFilter(query, normalizeUpper(criteria.subTypes()));
        if (criteria.minRating() != null) {
            query.add(DoublePoint.newRangeQuery(
                            RATING_FIELD, criteria.minRating().doubleValue(), Double.POSITIVE_INFINITY),
                    BooleanClause.Occur.FILTER);
        }
        if (criteria.citySlug() != null && !criteria.citySlug().isBlank()) {
            String normalizedCity = DestinationMatchUtils.normalizeKey(
                    CitySlugResolver.normalizeRequired(criteria.citySlug()));
            query.add(new TermQuery(new Term(CITY_SLUG_FIELD, normalizedCity)), BooleanClause.Occur.FILTER);
        }
        if (criteria.foodIds() != null && !criteria.foodIds().isEmpty()) {
            addTermSetFilter(query, FOOD_ID_FIELD, criteria.foodIds().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(UUID::toString)
                    .toList());
        }
        if (Boolean.TRUE.equals(criteria.excludeLinkedFoodPlaces())) {
            query.add(new TermQuery(new Term(HAS_LINKED_FOOD_FIELD, Boolean.FALSE.toString())),
                    BooleanClause.Occur.FILTER);
        }
        return query.build();
    }

    /**
     * Places with any of the kinds; {@link PlaceSubType#UNTYPED_FILTER} among them also lets
     * through the places that have no kind at all.
     */
    private void addSubTypeFilter(BooleanQuery.Builder query, List<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        List<String> kinds = codes.stream().filter(code -> !PlaceSubType.UNTYPED_FILTER.equals(code)).toList();
        if (kinds.size() == codes.size()) {
            addTermSetFilter(query, SUB_TYPE_FIELD, kinds);
            return;
        }
        BooleanQuery.Builder either = new BooleanQuery.Builder();
        if (!kinds.isEmpty()) {
            either.add(new TermInSetQuery(SUB_TYPE_FIELD, toBytesRefs(kinds)), BooleanClause.Occur.SHOULD);
        }
        either.add(untypedQuery(), BooleanClause.Occur.SHOULD);
        query.add(either.build(), BooleanClause.Occur.FILTER);
    }

    /** Places that carry none of the kinds. */
    private static Query untypedQuery() {
        return new BooleanQuery.Builder()
                .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                .add(new TermInSetQuery(SUB_TYPE_FIELD, toBytesRefs(
                                Arrays.stream(PlaceSubType.values()).map(Enum::name).toList())),
                        BooleanClause.Occur.MUST_NOT)
                .build();
    }

    private static List<BytesRef> toBytesRefs(Collection<String> values) {
        return values.stream().map(BytesRef::new).toList();
    }

    @Override
    public Map<String, Integer> countBySubType(PlaceSearchCriteria criteria, List<PlaceSubType> kinds)
            throws IOException {
        validateCriteria(criteria);
        IndexSearcher searcher = searcherManager.acquire();
        try {
            // The area as the list searches it, before any kind is chosen.
            Query area = buildSearchQuery(searcher, withoutSubTypes(criteria));
            // One pass: the area (distance, text, filters) is evaluated once per segment into a
            // bit set, then each kind's posting list is walked against it. Counting each kind
            // with its own query evaluated the area once per kind.
            int[] kindCounts = new int[kinds.size()];
            int areaCount = 0;
            int typedCount = 0;
            org.apache.lucene.search.Weight weight = searcher.createWeight(
                    searcher.rewrite(area), org.apache.lucene.search.ScoreMode.COMPLETE_NO_SCORES, 1f);
            for (LeafReaderContext leaf : searcher.getIndexReader().leaves()) {
                org.apache.lucene.search.Scorer scorer = weight.scorer(leaf);
                if (scorer == null) {
                    continue;
                }
                org.apache.lucene.util.Bits live = leaf.reader().getLiveDocs();
                org.apache.lucene.util.FixedBitSet inArea = new org.apache.lucene.util.FixedBitSet(leaf.reader().maxDoc());
                org.apache.lucene.search.DocIdSetIterator docs = scorer.iterator();
                for (int doc = docs.nextDoc(); doc != org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;
                     doc = docs.nextDoc()) {
                    if (live == null || live.get(doc)) {
                        inArea.set(doc);
                    }
                }
                areaCount += inArea.cardinality();
                // Any known kind makes a place typed, listed or not: the same rule as untypedQuery().
                org.apache.lucene.util.FixedBitSet typed = new org.apache.lucene.util.FixedBitSet(leaf.reader().maxDoc());
                for (PlaceSubType known : PlaceSubType.values()) {
                    org.apache.lucene.index.PostingsEnum postings =
                            leaf.reader().postings(new Term(SUB_TYPE_FIELD, known.name()));
                    if (postings == null) {
                        continue;
                    }
                    int listed = kinds.indexOf(known);
                    for (int doc = postings.nextDoc(); doc != org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;
                         doc = postings.nextDoc()) {
                        if (!inArea.get(doc)) {
                            continue;
                        }
                        typed.set(doc);
                        if (listed >= 0) {
                            kindCounts[listed]++;
                        }
                    }
                }
                typedCount += typed.cardinality();
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (int i = 0; i < kinds.size(); i++) {
                counts.put(kinds.get(i).name(), kindCounts[i]);
            }
            counts.put(PlaceSubType.UNTYPED_FILTER, areaCount - typedCount);
            return counts;
        } finally {
            searcherManager.release(searcher);
        }
    }

    @Override
    public Map<MapTile, List<UUID>> findMapTilePlaceIds(Collection<MapTile> tiles, List<String> placeGroups,
                                                       List<String> subTypes) throws IOException {
        Map<MapTile, List<UUID>> result = new LinkedHashMap<>();
        if (tiles == null || tiles.isEmpty()) {
            return result;
        }
        int zoom = tiles.iterator().next().z();
        if (tiles.stream().anyMatch(tile -> tile.z() != zoom)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Map tiles must share one zoom");
        }
        double south = MapTile.MAX_LATITUDE;
        double north = -MapTile.MAX_LATITUDE;
        double west = 180.0;
        double east = -180.0;
        Set<Long> wantedTiles = new HashSet<>();
        for (MapTile tile : tiles) {
            south = Math.min(south, tile.south());
            north = Math.max(north, tile.north());
            west = Math.min(west, tile.west());
            east = Math.max(east, tile.east());
            wantedTiles.add(cellKey(tile.x(), tile.y()));
        }

        BooleanQuery.Builder query = new BooleanQuery.Builder();
        query.add(LatLonPoint.newBoxQuery(LOCATION_FIELD, south, north, west, east), BooleanClause.Occur.FILTER);
        query.add(new TermQuery(new Term(VISIBILITY_FIELD, "ACTIVE")), BooleanClause.Occur.FILTER);
        query.add(PERMANENTLY_CLOSED, BooleanClause.Occur.MUST_NOT);
        addTermSetFilter(query, PLACE_GROUP_FIELD, normalizeUpper(placeGroups));
        addSubTypeFilter(query, normalizeUpper(subTypes));

        MapTileCellCollector collector = new MapTileCellCollector(zoom + MAP_TILE_CELL_DEPTH, wantedTiles);
        Map<MapTile, List<UUID>> found = new HashMap<>();
        IndexSearcher searcher = searcherManager.acquire();
        try {
            searcher.search(query.build(), collector);
            List<Map.Entry<Long, MapTileCellCollector.Best>> winners = new ArrayList<>(collector.bestByCell.entrySet());
            winners.sort((a, b) -> Double.compare(b.getValue().prominence(), a.getValue().prominence()));
            for (Map.Entry<Long, MapTileCellCollector.Best> winner : winners) {
                long cell = winner.getKey();
                MapTile tile = new MapTile(zoom,
                        (int) ((cell >>> 32) >> MAP_TILE_CELL_DEPTH),
                        (int) ((cell & 0xFFFFFFFFL) >> MAP_TILE_CELL_DEPTH));
                Document doc = searcher.doc(winner.getValue().doc(), Set.of(ID_FIELD));
                found.computeIfAbsent(tile, ignored -> new ArrayList<>()).add(UUID.fromString(doc.get(ID_FIELD)));
            }
        } finally {
            searcherManager.release(searcher);
        }
        for (MapTile tile : tiles) {
            result.put(tile, found.getOrDefault(tile, List.of()));
        }
        return result;
    }

    private static long cellKey(long x, long y) {
        return (x << 32) | y;
    }

    /**
     * One pass over the matching places, keeping the most prominent in each cell of the
     * requested tiles. No sort over the whole match: a country-wide tile is one read of two
     * doc values per place.
     */
    private static final class MapTileCellCollector extends SimpleCollector {

        record Best(double prominence, int doc) {
        }

        private final long cellsAcross;
        private final Set<Long> wantedTiles;
        private final Map<Long, Best> bestByCell = new HashMap<>();
        private SortedNumericDocValues locations;
        private NumericDocValues prominences;
        private int docBase;

        MapTileCellCollector(int cellZoom, Set<Long> wantedTiles) {
            this.cellsAcross = 1L << cellZoom;
            this.wantedTiles = wantedTiles;
        }

        @Override
        protected void doSetNextReader(LeafReaderContext context) throws IOException {
            docBase = context.docBase;
            locations = DocValues.getSortedNumeric(context.reader(), LOCATION_FIELD);
            prominences = DocValues.getNumeric(context.reader(), PROMINENCE_FIELD);
        }

        @Override
        public void collect(int doc) throws IOException {
            if (!locations.advanceExact(doc)) {
                return;
            }
            long encoded = locations.nextValue();
            double latitude = GeoEncodingUtils.decodeLatitude((int) (encoded >>> 32));
            double longitude = GeoEncodingUtils.decodeLongitude((int) encoded);
            long cellX = cellIndex(MapTile.unitX(longitude));
            long cellY = cellIndex(MapTile.unitY(latitude));
            if (!wantedTiles.contains(cellKey(cellX >> MAP_TILE_CELL_DEPTH, cellY >> MAP_TILE_CELL_DEPTH))) {
                return;
            }
            double prominence = prominences.advanceExact(doc)
                    ? Double.longBitsToDouble(prominences.longValue())
                    : 0.0;
            long key = cellKey(cellX, cellY);
            Best best = bestByCell.get(key);
            if (best == null || prominence > best.prominence()) {
                bestByCell.put(key, new Best(prominence, docBase + doc));
            }
        }

        private long cellIndex(double unit) {
            return Math.max(0L, Math.min(cellsAcross - 1, (long) Math.floor(unit * cellsAcross)));
        }

        @Override
        public ScoreMode scoreMode() {
            return ScoreMode.COMPLETE_NO_SCORES;
        }
    }

    private static PlaceSearchCriteria withoutSubTypes(PlaceSearchCriteria criteria) {
        return new PlaceSearchCriteria(
                criteria.keyword(), criteria.latitude(), criteria.longitude(), criteria.radiusKm(),
                criteria.category(), criteria.placeGroups(), null, criteria.minRating(),
                criteria.citySlug(), criteria.foodIds(), criteria.excludeLinkedFoodPlaces(),
                criteria.includeInactive(), criteria.sort(), criteria.minLuceneScore(),
                criteria.page(), criteria.size());
    }

    private void addTermSetFilter(BooleanQuery.Builder query, String field, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        List<BytesRef> terms = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(BytesRef::new)
                .toList();
        if (!terms.isEmpty()) {
            query.add(new TermInSetQuery(field, terms), BooleanClause.Occur.FILTER);
        }
    }

    private void validateCriteria(PlaceSearchCriteria criteria) {
        if (criteria == null || criteria.latitude() == null || criteria.longitude() == null
                || criteria.radiusKm() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "latitude, longitude and radius are required");
        }
        double latitude = criteria.latitude().doubleValue();
        double longitude = criteria.longitude().doubleValue();
        double radiusKm = criteria.radiusKm().doubleValue();
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(radiusKm) || radiusKm <= 0) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Invalid latitude, longitude or radius");
        }
        if (criteria.page() < 0 || criteria.size() < 1 || criteria.size() > PlaceSearchCriteria.MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "page must be >= 0 and size must be between 1 and " + PlaceSearchCriteria.MAX_PAGE_SIZE);
        }
        try {
            Math.addExact(Math.multiplyExact(criteria.page(), criteria.size()), criteria.size());
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Requested page is too large");
        }
    }

    /**
     * The place's usage counts (V225), which rank it. A place loaded from its row has them; one
     * built by its caller (a new place, an edit) is read back for them, and counts 0 when it is
     * not stored yet.
     */
    private Place withUsageCounts(Place place) {
        if (place.getTripUseCount() != null && place.getCheckinCount() != null) {
            return place;
        }
        return placeRepository.findUsageCounts(place.getId()).orElse(place);
    }

    /**
     * Where a place is, as the index reads it: its address and the names of the ward and
     * province it lies in.
     */
    private record Where(String address, List<String> areaNames) {
    }

    /**
     * {@link Where} of a place handed in by its caller. Such an object may not carry the address
     * or the ward (a new place, an edit; the ward is resolved outside the generic update), so
     * what it lacks is read back from its row.
     */
    private Where whereOf(Place place) {
        if (place.getAddress() != null && place.getWardCode() != null) {
            return whereOfRow(place);
        }
        Place row = placeRepository.findSearchIndexRow(place.getId()).orElse(null);
        if (row == null) {
            return whereOfRow(place);
        }
        String wardCode = place.getWardCode() != null ? place.getWardCode() : row.getWardCode();
        return whereOf(
                place.getAddress() != null ? place.getAddress() : row.getAddress(),
                wardCode == null ? null : wardRepository.findByCode(wardCode).orElse(null),
                place.getProvinceCode() != null ? place.getProvinceCode() : row.getProvinceCode());
    }

    /** {@link Where} of a place read with the columns the index reads. */
    private Where whereOfRow(Place place) {
        Ward ward = place.getWardCode() == null ? null : wardRepository.findByCode(place.getWardCode()).orElse(null);
        return whereOf(place.getAddress(), ward, place.getProvinceCode());
    }

    private Where whereOf(String address, Ward ward, String provinceCode) {
        List<String> areaNames = new ArrayList<>();
        String provinceName = null;
        if (ward != null) {
            areaNames.add(ward.getName());
            areaNames.add(ward.getNameEn());
            provinceName = ward.getProvinceName();
        }
        if (provinceName == null) {
            provinceName = provinceNameResolver.nameOf(provinceCode);
        }
        areaNames.add(provinceName);
        return new Where(address, areaNames);
    }

    /** The wards of a page of places, by code, in one read. */
    private Map<String, Ward> findWards(List<Place> places) {
        Set<String> codes = places.stream()
                .map(Place::getWardCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, Ward> wards = new HashMap<>();
        for (Ward ward : wardRepository.findByCodes(codes)) {
            wards.put(ward.getCode(), ward);
        }
        return wards;
    }

    private Document toDocument(Place place, List<PlaceTranslation> translations, Set<UUID> foodIds, Where where) {
        Document doc = new Document();
        doc.add(new StringField(ID_FIELD, place.getId().toString(), Field.Store.YES));
        doc.add(new StringField("schema_version", INDEX_SCHEMA_VERSION, Field.Store.YES));
        Set<String> names = searchableNames(place, translations);
        doc.add(new TextField(NAME_FIELD, LuceneKeywordQuery.normalize(String.join(" ", names)), Field.Store.YES));
        Set<String> joined = new LinkedHashSet<>();
        names.forEach(name -> joined.addAll(LuceneKeywordQuery.joinedWords(name)));
        if (!joined.isEmpty()) {
            doc.add(new TextField(NAME_JOINED_FIELD, String.join(" ", joined), Field.Store.NO));
        }
        names.stream()
                .map(name -> LuceneKeywordQuery.tokens(name, MAX_EXACT_NAME_WORDS).stream()
                        .map(LuceneKeywordQuery.Word::folded)
                        .collect(Collectors.joining(" ")))
                .distinct()
                .forEach(name -> addKeywordField(doc, NAME_EXACT_FIELD, name));
        PlaceAddressTerms.Terms address = PlaceAddressTerms.of(where.address(), where.areaNames());
        address.pairs().forEach(pair -> addKeywordField(doc, ADDRESS_PAIR_FIELD, pair));
        address.singles().forEach(single -> addKeywordField(doc, ADDRESS_SINGLE_FIELD, single));
        address.houseNumbers().forEach(number -> addKeywordField(doc, HOUSE_NUMBER_FIELD, number));
        PlaceCategoryTerms.Terms kind = PlaceCategoryTerms.of(
                place.getPlaceGroup(), place.getSubTypes(), place.getCategory());
        kind.pairs().forEach(pair -> addKeywordField(doc, KIND_PAIR_FIELD, pair));
        kind.singles().forEach(single -> addKeywordField(doc, KIND_SINGLE_FIELD, single));

        if (place.getLatitude() != null && place.getLongitude() != null) {
            double latitude = place.getLatitude().doubleValue();
            double longitude = place.getLongitude().doubleValue();
            if (latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180) {
                doc.add(new LatLonPoint(LOCATION_FIELD, latitude, longitude));
                doc.add(new LatLonDocValuesField(LOCATION_FIELD, latitude, longitude));
            }
        }
        if (place.getVisibilityStatus() != null) {
            doc.add(new StringField(VISIBILITY_FIELD, place.getVisibilityStatus().name(), Field.Store.NO));
        }
        doc.add(new StringField(CLOSED_FIELD,
                PlaceBusinessStatus.fromStoredStatus(place.getStatus()) == PlaceBusinessStatus.PERMANENTLY_CLOSED
                        ? "1" : "0",
                Field.Store.NO));
        addKeywordField(doc, CATEGORY_FIELD, normalizeLower(place.getCategory()));
        if (place.getPlaceGroup() != null) {
            addKeywordField(doc, PLACE_GROUP_FIELD, place.getPlaceGroup().name());
        }
        if (place.getSubTypes() != null) {
            for (String subType : place.getSubTypes()) {
                addKeywordField(doc, SUB_TYPE_FIELD, subType);
            }
        }
        if (place.getReviewRating() != null) {
            double reviewRating = place.getReviewRating().doubleValue();
            doc.add(new DoublePoint(RATING_FIELD, reviewRating));
        }
        if (place.getAdjustedRating() != null) {
            doc.add(new DoubleDocValuesField(
                    EFFECTIVE_RATING_SORT_FIELD, place.getAdjustedRating().doubleValue()));
        }
        Place usage = withUsageCounts(place);
        doc.add(new DoubleDocValuesField(PROMINENCE_FIELD, PlaceProminence.of(
                place.getId(), usage.getTripUseCount(), usage.getCheckinCount(), place.getReviewCount())));
        List<String> destinations = JsonUtils.fromJson(
                place.getDestinations(), new TypeReference<List<String>>() {});
        if (destinations != null) {
            destinations.stream()
                    .map(DestinationMatchUtils::normalizeKey)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .forEach(value -> addKeywordField(doc, CITY_SLUG_FIELD, value));
        }

        Set<UUID> safeFoodIds = foodIds != null ? foodIds : Set.of();
        safeFoodIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(UUID::toString)
                .forEach(value -> addKeywordField(doc, FOOD_ID_FIELD, value));
        addKeywordField(doc, HAS_LINKED_FOOD_FIELD, Boolean.toString(!safeFoodIds.isEmpty()));
        return doc;
    }

    private void addKeywordField(Document doc, String field, String value) {
        if (value != null && !value.isBlank()) {
            doc.add(new StringField(field, value, Field.Store.NO));
        }
    }

    private Set<UUID> findFoodIds(UUID placeId) {
        return foodRepository.findFoodTagsByPlaceIds(List.of(placeId)).stream()
                .map(FoodTagRow::getFoodId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<UUID, Set<UUID>> groupFoodIds(List<FoodTagRow> rows) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        for (FoodTagRow row : rows) {
            if (row.getPlaceId() != null && row.getFoodId() != null) {
                result.computeIfAbsent(row.getPlaceId(), ignored -> new HashSet<>()).add(row.getFoodId());
            }
        }
        return result;
    }

    /** Every name the place is found by: its title, its search aliases (V284) and its translated names. */
    private Set<String> searchableNames(Place place, List<PlaceTranslation> translations) {
        Set<String> names = new LinkedHashSet<>();
        addSearchName(names, place.getTitle());
        for (String alias : searchAliases(place)) {
            addSearchName(names, alias);
        }
        if (translations != null) {
            translations.forEach(translation -> addSearchName(names, translation.getName()));
        }
        return names;
    }

    /**
     * The place's search aliases. A place loaded from its row has them (an empty array when it
     * has none); one built by its caller or read through a projection without the column has
     * null, and they are read back, so indexing such an object never drops them.
     */
    private String[] searchAliases(Place place) {
        if (place.getSearchAliases() != null) {
            return place.getSearchAliases();
        }
        String[] stored = placeRepository.findSearchIndexRow(place.getId())
                .map(Place::getSearchAliases)
                .orElse(null);
        return stored != null ? stored : new String[0];
    }

    private void addSearchName(Set<String> names, String name) {
        if (name != null && !name.isBlank()) {
            names.add(name.trim());
        }
    }

    private String normalizeLower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeUpper(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .toList();
    }

    private boolean isCurrentSchema(IndexSearcher searcher) throws IOException {
        TopDocs docs = searcher.search(new MatchAllDocsQuery(), 1);
        if (docs.scoreDocs.length == 0) {
            return true;
        }
        Document doc = searcher.doc(docs.scoreDocs[0].doc);
        return INDEX_SCHEMA_VERSION.equals(doc.get("schema_version")) && doc.get(NAME_FIELD) != null;
    }

    private void refreshSearchIndex() {
        try {
            searcherManager.maybeRefresh();
        } catch (IOException e) {
            log.warn("Failed to refresh place Lucene searcher: {}", e.getMessage(), e);
        }
    }
}

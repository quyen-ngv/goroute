package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.entity.FoodTagRow;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PlaceTranslationMapper;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.type.PlaceSubType;
import com.ds.goroute.utils.CitySlugResolver;
import com.ds.goroute.utils.DestinationMatchUtils;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.LuceneTitleQueryBuilder;
import com.ds.goroute.utils.MapTile;
import com.ds.goroute.utils.PlaceProminence;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
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
    private static final String INDEX_SCHEMA_VERSION = "9";
    private static final String ID_FIELD = "id";
    private static final String NAME_FIELD = "name";
    private static final String LOCATION_FIELD = "location";
    private static final String VISIBILITY_FIELD = "visibility";
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
    private static final Sort EFFECTIVE_RATING_DESCENDING_SORT = new Sort(
            new SortField(EFFECTIVE_RATING_SORT_FIELD, SortField.Type.DOUBLE, true));
    private static final Sort PROMINENCE_DESCENDING_SORT = new Sort(
            new SortField(PROMINENCE_FIELD, SortField.Type.DOUBLE, true));

    private final PlaceRepository placeRepository;
    private final PlaceTranslationMapper placeTranslationMapper;
    private final FoodRepository foodRepository;

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
        analyzer = createSearchAnalyzer();
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

    private Analyzer createSearchAnalyzer() {
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
                    place, placeTranslationMapper.findByPlaceId(place.getId()), findFoodIds(place.getId()));
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
            int total;
            try (Directory rebuildDirectory = FSDirectory.open(rebuildDir)) {
                try (IndexWriter rebuildWriter = new IndexWriter(rebuildDirectory, new IndexWriterConfig(analyzer))) {
                    total = writeAllPlaces(rebuildWriter, generation);
                    rebuildWriter.commit();
                }
                swapIn(rebuildDirectory, generation);
            }
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
            for (Place place : batch) {
                Document document = toDocument(
                        place,
                        translationsByPlaceId.getOrDefault(place.getId(), List.of()),
                        foodIdsByPlaceId.getOrDefault(place.getId(), Set.of()));
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
                            current, placeTranslationMapper.findByPlaceId(id), findFoodIds(id)));
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
        if (keyword == null || keyword.isBlank()) {
            return 0;
        }
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        try {
            query.add(LuceneTitleQueryBuilder.build(keyword.trim(), analyzer), BooleanClause.Occur.MUST);
        } catch (IllegalArgumentException noSearchableText) {
            return 0;
        }
        query.add(new TermQuery(new Term(VISIBILITY_FIELD, "ACTIVE")), BooleanClause.Occur.FILTER);
        addTermSetFilter(query, PLACE_GROUP_FIELD, normalizeUpper(placeGroups));
        IndexSearcher searcher = searcherManager.acquire();
        try {
            return searcher.count(query.build());
        } finally {
            searcherManager.release(searcher);
        }
    }

    @Override
    public List<UUID> searchPlaceIdsByName(String keyword, List<String> placeGroups, boolean includeInactive,
                                           int page, int size) throws IOException {
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
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        try {
            query.add(LuceneTitleQueryBuilder.build(keyword.trim(), analyzer), BooleanClause.Occur.MUST);
        } catch (IllegalArgumentException noSearchableText) {
            return List.of();
        }
        if (!includeInactive) {
            query.add(new TermQuery(new Term(VISIBILITY_FIELD, "ACTIVE")), BooleanClause.Occur.FILTER);
        }
        addTermSetFilter(query, PLACE_GROUP_FIELD, normalizeUpper(placeGroups));

        IndexSearcher searcher = searcherManager.acquire();
        try {
            ScoreDoc[] hits = searcher.search(query.build(), requestedHits).scoreDocs;
            List<UUID> ids = new ArrayList<>();
            for (int index = offset; index < hits.length; index++) {
                ids.add(UUID.fromString(searcher.doc(hits[index].doc).get(ID_FIELD)));
            }
            return ids;
        } finally {
            searcherManager.release(searcher);
        }
    }

    @Override
    public List<UUID> searchPlaceIds(PlaceSearchCriteria criteria) throws IOException {
        validateCriteria(criteria);
        int offset = Math.multiplyExact(criteria.page(), criteria.size());
        int requestedHits = Math.addExact(offset, criteria.size());
        Query query = buildSearchQuery(criteria);

        IndexSearcher searcher = searcherManager.acquire();
        try {
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
                Document doc = searcher.doc(scoreDoc.doc);
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

    private Query buildSearchQuery(PlaceSearchCriteria criteria) throws IOException {
        double latitude = criteria.latitude().doubleValue();
        double longitude = criteria.longitude().doubleValue();
        double radiusMeters = criteria.radiusKm().doubleValue() * 1_000.0;
        BooleanQuery.Builder query = new BooleanQuery.Builder();

        if (criteria.keyword() == null || criteria.keyword().isBlank()) {
            query.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        } else {
            query.add(LuceneTitleQueryBuilder.build(criteria.keyword().trim(), analyzer), BooleanClause.Occur.MUST);
        }

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
        // The area as the list searches it, before any kind is chosen.
        Query area = buildSearchQuery(withoutSubTypes(criteria));
        IndexSearcher searcher = searcherManager.acquire();
        try {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (PlaceSubType kind : kinds) {
                counts.put(kind.name(), searcher.count(new BooleanQuery.Builder()
                        .add(area, BooleanClause.Occur.MUST)
                        .add(new TermQuery(new Term(SUB_TYPE_FIELD, kind.name())), BooleanClause.Occur.FILTER)
                        .build()));
            }
            counts.put(PlaceSubType.UNTYPED_FILTER, searcher.count(new BooleanQuery.Builder()
                    .add(area, BooleanClause.Occur.MUST)
                    .add(untypedQuery(), BooleanClause.Occur.FILTER)
                    .build()));
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

    private Document toDocument(Place place, List<PlaceTranslation> translations, Set<UUID> foodIds) {
        Document doc = new Document();
        doc.add(new StringField(ID_FIELD, place.getId().toString(), Field.Store.YES));
        doc.add(new StringField("schema_version", INDEX_SCHEMA_VERSION, Field.Store.YES));
        doc.add(new TextField(NAME_FIELD, buildSearchableName(place, translations), Field.Store.YES));

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

    private String buildSearchableName(Place place, List<PlaceTranslation> translations) {
        Set<String> names = new LinkedHashSet<>();
        addSearchName(names, place.getTitle());
        if (translations != null) {
            translations.forEach(translation -> addSearchName(names, translation.getName()));
        }
        return String.join(" ", names);
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

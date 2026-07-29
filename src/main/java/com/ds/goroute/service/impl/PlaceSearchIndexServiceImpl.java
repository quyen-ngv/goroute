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
import com.ds.goroute.utils.CitySlugResolver;
import com.ds.goroute.utils.DestinationMatchUtils;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.LuceneTitleQueryBuilder;
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
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
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
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceSearchIndexServiceImpl implements PlaceSearchIndexService {

    private static final int REINDEX_BATCH_SIZE = 200;
    private static final String INDEX_SCHEMA_VERSION = "3";
    private static final String ID_FIELD = "id";
    private static final String NAME_FIELD = "name";
    private static final String LOCATION_FIELD = "location";
    private static final String VISIBILITY_FIELD = "visibility";
    private static final String CATEGORY_FIELD = "category";
    private static final String PLACE_GROUP_FIELD = "place_group";
    private static final String RATING_FIELD = "review_rating";
    private static final String CITY_SLUG_FIELD = "city_slug";
    private static final String FOOD_ID_FIELD = "food_id";
    private static final String HAS_LINKED_FOOD_FIELD = "has_linked_food";
    private static final float DISTANCE_SCORE_WEIGHT = 1.5f;
    private static final double MIN_DISTANCE_PIVOT_METERS = 100.0;
    private static final double MAX_DISTANCE_PIVOT_METERS = 5_000.0;

    private final PlaceRepository placeRepository;
    private final PlaceTranslationMapper placeTranslationMapper;
    private final FoodRepository foodRepository;

    @Value("${goroute.search.place-index-path:data/lucene-index/places}")
    private String indexPath;

    private Directory directory;
    private IndexWriter writer;
    private SearcherManager searcherManager;
    private Analyzer analyzer;

    @PostConstruct
    public void init() throws IOException {
        analyzer = createSearchAnalyzer();
        Path indexDir = Paths.get(indexPath);
        Files.createDirectories(indexDir);
        directory = FSDirectory.open(indexDir);
        writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
        searcherManager = new SearcherManager(writer, new SearcherFactory());
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

    private void ensureSearchIndexPopulated() {
        try {
            IndexSearcher searcher = searcherManager.acquire();
            try {
                int docCount = searcher.getIndexReader().numDocs();
                if (docCount == 0 && placeRepository.countAll() > 0) {
                    log.warn("Place Lucene index is empty but DB has data — reindexing");
                    triggerReindex();
                    return;
                }
                if (docCount > 0 && !isCurrentSchema(searcher)) {
                    log.warn("Place Lucene index schema is outdated; reindexing");
                    triggerReindex();
                }
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            log.warn("Could not verify place Lucene index: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() throws IOException {
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
            writer.updateDocument(new Term(ID_FIELD, place.getId().toString()),
                    toDocument(place, placeTranslationMapper.findByPlaceId(place.getId()), findFoodIds(place.getId())));
            writer.commit();
            refreshSearchIndex();
        } catch (Exception e) {
            log.error("Failed to index place {}", place.getId(), e);
        }
    }

    @Override
    public void deletePlace(UUID id) {
        if (id == null) {
            return;
        }
        try {
            writer.deleteDocuments(new Term(ID_FIELD, id.toString()));
            writer.commit();
            refreshSearchIndex();
        } catch (IOException e) {
            log.error("Failed to delete place {} from index", id, e);
        }
    }

    @Override
    public void triggerReindex() {
        try {
            writer.deleteAll();

            int offset = 0;
            int total = 0;
            while (true) {
                List<Place> batch = placeRepository.findPage(REINDEX_BATCH_SIZE, offset);
                if (batch.isEmpty()) {
                    break;
                }
                List<UUID> placeIds = batch.stream().map(Place::getId).toList();
                Map<UUID, List<PlaceTranslation>> translationsByPlaceId = placeTranslationMapper.findByPlaceIds(placeIds)
                        .stream()
                        .collect(Collectors.groupingBy(PlaceTranslation::getPlaceId));
                Map<UUID, Set<UUID>> foodIdsByPlaceId = groupFoodIds(foodRepository.findFoodTagsByPlaceIds(placeIds));
                for (Place place : batch) {
                    writer.addDocument(toDocument(
                            place,
                            translationsByPlaceId.getOrDefault(place.getId(), List.of()),
                            foodIdsByPlaceId.getOrDefault(place.getId(), Set.of())));
                }
                total += batch.size();
                offset += batch.size();
            }

            writer.commit();
            refreshSearchIndex();
            log.info("Reindexed {} places", total);
        } catch (Exception e) {
            log.error("Place reindex failed", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Place reindex failed");
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
            TopDocs topDocs = searcher.search(query, requestedHits);
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
        if (place.getReviewRating() != null) {
            doc.add(new DoublePoint(RATING_FIELD, place.getReviewRating().doubleValue()));
        }
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
            log.warn("Failed to refresh place Lucene searcher: {}", e.getMessage());
        }
    }
}

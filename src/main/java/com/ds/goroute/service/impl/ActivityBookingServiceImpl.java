package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.GeoCoordinateDto;
import com.ds.goroute.dto.request.AddBookingToTripRequest;
import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.request.ImportActivityBookingRequest;
import com.ds.goroute.dto.request.UpdateActivityBookingRequest;
import com.ds.goroute.dto.response.ActivityBookingResponse;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.ActivityBooking;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.ActivityBookingGeoSearchParams;
import com.ds.goroute.repository.ActivityBookingRepository;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.ActivityBookingService;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.utils.ActivityBookingSearchFieldHelper;
import com.ds.goroute.utils.DestinationMatchUtils;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.LuceneTitleQueryBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityBookingServiceImpl implements ActivityBookingService {

    private static final int MAX_GEO_FETCH = 250;
    private static final int MAX_LUCENE_FETCH = 500;
    private static final int REINDEX_BATCH_SIZE = 200;

    private final ActivityBookingRepository repository;
    private final PlaceRepository placeRepository;
    private final TripRepository tripRepository;
    private final ActivityService activityService;
    private final ExchangeRateService exchangeRateService;
    private final ImageStorageCleanupService imageStorageCleanupService;

    private Directory directory;
    private IndexWriter writer;
    private SearcherManager searcherManager;
    private StandardAnalyzer analyzer;

    @PostConstruct
    public void init() throws IOException {
        analyzer = new StandardAnalyzer();
        java.nio.file.Path indexDir = Paths.get("data", "lucene-index", "activity-bookings");
        java.nio.file.Files.createDirectories(indexDir);
        directory = FSDirectory.open(indexDir);
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        writer = new IndexWriter(directory, config);
        searcherManager = new SearcherManager(writer, new SearcherFactory());
        log.info("Lucene index initialized at: {}", indexDir.toAbsolutePath());
        ensureSearchIndexPopulated();
    }

    private void ensureSearchIndexPopulated() {
        try {
            IndexSearcher searcher = searcherManager.acquire();
            try {
                if (searcher.getIndexReader().numDocs() == 0 && repository.countAll() > 0) {
                    log.warn("Activity booking Lucene index is empty but DB has data — reindexing");
                    triggerReindex();
                }
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            log.warn("Could not verify activity booking Lucene index: {}", e.getMessage());
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
    @Transactional
    public ActivityBookingResponse importFromKlook(ImportActivityBookingRequest request) {
        String externalId = extractExternalId(request.getUrl());

        Optional<ActivityBooking> existing = repository.findByExternalId(externalId);
        if (existing.isPresent()) {
            log.warn("Activity already exists, updating: {}", externalId);
            return updateExisting(existing.get(), request);
        }

        String departingFrom = extractDepartingFrom(request.getLocation());
        BigDecimal durationHours = parseDuration(request.getDuration());
        List<String> destinations = normalizeDestinations(request.getDestinations(), request.getLocation());
        List<GeoCoordinateDto> destinationCoordinates = resolveDestinationCoordinates(request);

        ActivityBooking booking = ActivityBooking.builder()
                .id(UUID.randomUUID())
                .externalId(externalId)
                .source("KLOOK")
                .url(request.getUrl())
                .redirectUrl(request.getUrl()) // Default = url
                .title(request.getTitle())
                .description(request.getDescription())
                .activityAddress(request.getLocation().getActivityAddress())
                .departingFrom(departingFrom)
                .destinations(JsonUtils.toJson(destinations))
                .destinationCoordinates(toJsonOrNull(destinationCoordinates))
                .navigationList(JsonUtils.toJson(request.getLocation().getNavigationList()))
                .itineraryStops(JsonUtils.toJson(request.getLocation().getItineraryStops()))
                .pickupAddresses(JsonUtils.toJson(request.getLocation().getPickupAddresses()))
                .priceAmount(request.getPrice().getAmount())
                .priceCurrency("USD")
                .durationRaw(request.getDuration())
                .durationHours(durationHours)
                .rating(request.getRating())
                .reviewCount(request.getReviewCount())
                .bookedCount(request.getBookedCount())
                .thumbnail(request.getImages() != null && !request.getImages().isEmpty() ? request.getImages().get(0) : null)
                .images(JsonUtils.toJson(request.getImages()))
                .highlights(JsonUtils.toJson(request.getHighlights()))
                .whatToExpect(JsonUtils.toJson(request.getWhatToExpect()))
                .itinerary(JsonUtils.toJson(request.getItinerary()))
                .build();

        ActivityBookingSearchFieldHelper.apply(booking);
        repository.insert(booking);

        try {
            indexActivityBooking(booking);
            writer.commit();
            refreshSearchIndex();
        } catch (IOException e) {
            log.error("Failed to index activity booking", e);
        }

        log.info("Imported activity booking: {}", booking.getId());
        return mapToResponse(booking);
    }

    @Override
    public List<ActivityBookingResponse> search(String query, BigDecimal minPrice, BigDecimal maxPrice,
                                                BigDecimal minRating, List<String> destinations,
                                                BigDecimal latitude, BigDecimal longitude,
                                                Double radiusKm, Float minLuceneScore,
                                                String targetCurrency, int page, int size) {
        List<String> destinationFilters = normalizeDestinationFilter(destinations);
        List<String> destinationSqlKeys = toSqlDestinationKeys(destinationFilters);
        boolean hasGeo = latitude != null && longitude != null;
        double radius = radiusKm != null && radiusKm > 0 ? radiusKm : 50.0;

        if (hasGeo) {
            int fetchLimit = computeFetchLimit(page, size);
            ActivityBookingGeoSearchParams geoParams = buildGeoSearchParams(
                    latitude, longitude, radius, minPrice, maxPrice, minRating, fetchLimit, 0);
            List<ActivityBooking> filtered = repository.findWithinRadius(geoParams).stream()
                    .filter(booking -> matchesDestinationsForGeo(booking, destinationFilters))
                    .collect(Collectors.toList());
            filtered = applyTextFilter(filtered, query, minLuceneScore);
            return paginateAndMap(filtered, page, size, targetCurrency);
        }

        boolean hasDestinationFilter = !destinationFilters.isEmpty();
        boolean hasTextOrNumericFilter = (query != null && !query.trim().isEmpty())
                || minPrice != null || maxPrice != null || minRating != null;

        if (!hasTextOrNumericFilter) {
            if (hasDestinationFilter) {
                int candidateLimit = computeFetchLimit(page, size) * 2;
                List<ActivityBooking> candidates = destinationSqlKeys.isEmpty()
                        ? repository.findPage(candidateLimit, 0)
                        : repository.findByDestinations(destinationSqlKeys, candidateLimit, 0);
                List<ActivityBooking> filtered = candidates.stream()
                        .filter(booking -> matchesDestinations(booking, destinationFilters))
                        .collect(Collectors.toList());
                return paginateAndMap(filtered, page, size, targetCurrency);
            }
            return repository.findPage(size, page * size).stream()
                    .map(b -> mapToResponse(b, targetCurrency))
                    .collect(Collectors.toList());
        }

        try {
            IndexSearcher searcher = searcherManager.acquire();
            try {
                BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();

                if (query != null && !query.trim().isEmpty()) {
                    Query textQuery = buildTextSearchQuery(query.trim());
                    booleanQuery.add(textQuery, BooleanClause.Occur.MUST);
                } else {
                    booleanQuery.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
                }

                if (minPrice != null || maxPrice != null) {
                    double min = minPrice != null ? minPrice.doubleValue() : 0;
                    double max = maxPrice != null ? maxPrice.doubleValue() : Double.MAX_VALUE;
                    Query priceQuery = DoublePoint.newRangeQuery("priceAmount", min, max);
                    booleanQuery.add(priceQuery, BooleanClause.Occur.MUST);
                }

                if (minRating != null) {
                    Query ratingQuery = DoublePoint.newRangeQuery(
                            "rating", minRating.doubleValue(), Double.MAX_VALUE);
                    booleanQuery.add(ratingQuery, BooleanClause.Occur.MUST);
                }

                int maxResults = Math.min(
                        MAX_LUCENE_FETCH,
                        Math.max((page + 1) * size * 3, size * 10));
                TopDocs topDocs = searcher.search(booleanQuery.build(), maxResults);

                List<UUID> orderedIds = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    if (minLuceneScore != null && scoreDoc.score < minLuceneScore) {
                        continue;
                    }
                    Document doc = searcher.doc(scoreDoc.doc);
                    orderedIds.add(UUID.fromString(doc.get("id")));
                }

                Map<UUID, ActivityBooking> bookingsById = repository.findByIds(orderedIds).stream()
                        .collect(Collectors.toMap(ActivityBooking::getId, booking -> booking, (a, b) -> a));

                List<ActivityBookingResponse> results = new ArrayList<>();
                int matched = 0;
                int offset = page * size;

                for (UUID id : orderedIds) {
                    ActivityBooking booking = bookingsById.get(id);
                    if (booking == null || !matchesDestinations(booking, destinationFilters)) {
                        continue;
                    }
                    if (matched++ < offset) {
                        continue;
                    }
                    results.add(mapToResponse(booking, targetCurrency));
                    if (results.size() >= size) {
                        break;
                    }
                }
                if (results.isEmpty() && query != null && !query.trim().isEmpty()) {
                    log.debug("No Lucene matches for activity booking title query: {}", query.trim());
                }
                return results;
            } finally {
                searcherManager.release(searcher);
            }
        } catch (Exception e) {
            log.error("Lucene search failed", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Search failed: " + e.getMessage());
        }
    }

    @Override
    public List<ActivityBookingResponse> searchByPlace(
            UUID placeId, String query, Double radiusKm, String targetCurrency, int page, int size) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Place not found"));

        if (place.getLatitude() != null && place.getLongitude() != null) {
            return search(query, null, null, null, null,
                    place.getLatitude(), place.getLongitude(), radiusKm, null, targetCurrency, page, size);
        }

        List<String> destinations = JsonUtils.fromJson(place.getDestinations(), new TypeReference<List<String>>() {});
        if (destinations == null || destinations.isEmpty()) {
            return Collections.emptyList();
        }

        return search(query, null, null, null, destinations, null, null, radiusKm, null, targetCurrency, page, size);
    }

    @Override
    public ActivityBookingResponse getById(UUID id, String targetCurrency) {
        ActivityBooking booking = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity booking not found"));
        return mapToResponse(booking, targetCurrency);
    }

    @Override
    @Transactional
    public ActivityBookingResponse updateById(UUID id, UpdateActivityBookingRequest request) {
        ActivityBooking booking = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity booking not found"));

        if (request.getUrl() != null) {
            booking.setUrl(request.getUrl());
        }
        if (request.getRedirectUrl() != null) {
            booking.setRedirectUrl(request.getRedirectUrl());
        }
        if (request.getTitle() != null) {
            booking.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            booking.setDescription(request.getDescription());
        }
        if (request.getActivityAddress() != null) {
            booking.setActivityAddress(request.getActivityAddress());
        }
        if (request.getDepartingFrom() != null) {
            booking.setDepartingFrom(request.getDepartingFrom());
        }
        if (request.getDestinations() != null) {
            booking.setDestinations(JsonUtils.toJson(request.getDestinations()));
        }
        if (request.getDestinationCoordinates() != null) {
            booking.setDestinationCoordinates(JsonUtils.toJson(request.getDestinationCoordinates()));
        }
        if (request.getNavigationList() != null) {
            booking.setNavigationList(JsonUtils.toJson(request.getNavigationList()));
        }
        if (request.getItineraryStops() != null) {
            booking.setItineraryStops(JsonUtils.toJson(request.getItineraryStops()));
        }
        if (request.getPickupAddresses() != null) {
            booking.setPickupAddresses(JsonUtils.toJson(request.getPickupAddresses()));
        }
        if (request.getPriceAmount() != null) {
            booking.setPriceAmount(request.getPriceAmount());
        }
        if (request.getPriceCurrency() != null) {
            booking.setPriceCurrency(request.getPriceCurrency());
        }
        if (request.getDuration() != null) {
            booking.setDurationRaw(request.getDuration());
            booking.setDurationHours(parseDuration(request.getDuration()));
        }
        if (request.getRating() != null) {
            booking.setRating(request.getRating());
        }
        if (request.getReviewCount() != null) {
            booking.setReviewCount(request.getReviewCount());
        }
        if (request.getBookedCount() != null) {
            booking.setBookedCount(request.getBookedCount());
        }
        if (request.getThumbnail() != null) {
            booking.setThumbnail(request.getThumbnail());
        }
        if (request.getImages() != null) {
            booking.setImages(JsonUtils.toJson(request.getImages()));
        }
        if (request.getHighlights() != null) {
            booking.setHighlights(JsonUtils.toJson(request.getHighlights()));
        }
        if (request.getWhatToExpect() != null) {
            List<Map<String, String>> whatToExpect = request.getWhatToExpect().stream()
                    .map(item -> {
                        Map<String, String> value = new HashMap<>();
                        value.put("text", item.getText());
                        value.put("image", item.getImage());
                        return value;
                    })
                    .collect(Collectors.toList());
            booking.setWhatToExpect(JsonUtils.toJson(whatToExpect));
        }
        if (request.getItinerary() != null) {
            booking.setItinerary(JsonUtils.toJson(request.getItinerary()));
        }

        ActivityBookingSearchFieldHelper.apply(booking);
        repository.update(booking);

        try {
            writer.deleteDocuments(new Term("id", id.toString()));
            indexActivityBooking(booking);
            writer.commit();
            refreshSearchIndex();
        } catch (IOException e) {
            log.error("Failed to reindex after update", e);
        }

        log.info("Updated activity booking: {}", id);
        return mapToResponse(booking);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        ActivityBooking booking = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity booking not found"));

        imageStorageCleanupService.deleteImagesForEntityRecord("ACTIVITY_BOOKING", id);
        repository.delete(id);

        try {
            writer.deleteDocuments(new Term("id", id.toString()));
            writer.commit();
            refreshSearchIndex();
            log.info("Deleted activity booking from index: {}", id);
        } catch (IOException e) {
            log.error("Failed to delete from index", e);
        }

        log.info("Deleted activity booking: {}", id);
    }

    @Override
    public void triggerReindex() {
        try {
            writer.deleteAll();

            int offset = 0;
            int total = 0;
            while (true) {
                List<ActivityBooking> batch = repository.findPage(REINDEX_BATCH_SIZE, offset);
                if (batch.isEmpty()) {
                    break;
                }
                for (ActivityBooking booking : batch) {
                    indexActivityBooking(booking);
                }
                total += batch.size();
                offset += batch.size();
            }

            writer.commit();
            refreshSearchIndex();
            log.info("Reindexed {} activity bookings", total);

        } catch (IOException e) {
            log.error("Reindex failed", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Reindex failed");
        }
    }

    private void indexActivityBooking(ActivityBooking booking) throws IOException {
        Document doc = new Document();

        doc.add(new StringField("id", booking.getId().toString(), Field.Store.YES));
        doc.add(new TextField("title", booking.getTitle() != null ? booking.getTitle() : "", Field.Store.YES));
        doc.add(new TextField(
                "description",
                booking.getDescription() != null ? booking.getDescription() : "",
                Field.Store.NO));

        String searchText = buildSearchText(booking);
        doc.add(new TextField("searchText", searchText, Field.Store.NO));

        doc.add(new StringField("departingFrom", booking.getDepartingFrom() != null ? booking.getDepartingFrom() : "", Field.Store.YES));

        double priceAmount = booking.getPriceAmount() != null ? booking.getPriceAmount().doubleValue() : 0.0;
        doc.add(new DoublePoint("priceAmount", priceAmount));
        doc.add(new StoredField("priceAmount", priceAmount));

        double rating = booking.getRating() != null ? booking.getRating().doubleValue() : 0.0;
        doc.add(new DoublePoint("rating", rating));
        doc.add(new StoredField("rating", rating));

        writer.addDocument(doc);
    }

    private String buildSearchText(ActivityBooking booking) {
        List<String> terms = new ArrayList<>();

        if (booking.getTitle() != null && !booking.getTitle().isBlank()) {
            terms.add(booking.getTitle());
            terms.add(booking.getTitle());
            terms.add(booking.getTitle());
        }

        if (booking.getDescription() != null && !booking.getDescription().isBlank()) {
            terms.add(booking.getDescription());
        }

        if (booking.getActivityAddress() != null) {
            terms.add(booking.getActivityAddress());
            terms.add(booking.getActivityAddress());
            terms.add(booking.getActivityAddress());
        }

        List<String> stops = JsonUtils.fromJson(booking.getItineraryStops(), new TypeReference<List<String>>() {});
        if (stops != null) {
            stops.forEach(stop -> {
                terms.add(stop);
                terms.add(stop);
            });
        }

        List<String> navList = JsonUtils.fromJson(booking.getNavigationList(), new TypeReference<List<String>>() {});
        if (navList != null) {
            navList.forEach(nav -> {
                terms.add(nav);
                if (Math.random() > 0.5) terms.add(nav);
            });
        }

        List<String> destinations = JsonUtils.fromJson(booking.getDestinations(), new TypeReference<List<String>>() {});
        if (destinations != null) {
            destinations.forEach(destination -> {
                terms.add(destination);
                terms.add(destination);
            });
        }

        List<String> pickups = JsonUtils.fromJson(booking.getPickupAddresses(), new TypeReference<List<String>>() {});
        if (pickups != null) {
            terms.addAll(pickups);
        }

        return String.join(" ", terms);
    }

    private Query buildTextSearchQuery(String query) throws Exception {
        return LuceneTitleQueryBuilder.build(query, analyzer);
    }

    private ActivityBookingResponse mapToResponse(ActivityBooking booking) {
        return mapToResponse(booking, null);
    }

    private ActivityBookingResponse mapToResponse(ActivityBooking booking, String targetCurrency) {
        List<String> images = JsonUtils.fromJson(booking.getImages(), new TypeReference<List<String>>() {});
        List<String> highlights = JsonUtils.fromJson(booking.getHighlights(), new TypeReference<List<String>>() {});
        List<String> destinations = JsonUtils.fromJson(booking.getDestinations(), new TypeReference<List<String>>() {});
        List<GeoCoordinateDto> destinationCoordinates = JsonUtils.fromJson(
                booking.getDestinationCoordinates(), new TypeReference<List<GeoCoordinateDto>>() {});
        List<String> navigationList = JsonUtils.fromJson(booking.getNavigationList(), new TypeReference<List<String>>() {});
        List<String> itineraryStops = JsonUtils.fromJson(booking.getItineraryStops(), new TypeReference<List<String>>() {});
        List<String> pickupAddresses = JsonUtils.fromJson(booking.getPickupAddresses(), new TypeReference<List<String>>() {});

        List<Map<String, String>> whatToExpectRaw = JsonUtils.fromJson(booking.getWhatToExpect(),
                new TypeReference<List<Map<String, String>>>() {});
        List<ActivityBookingResponse.WhatToExpectResponse> whatToExpect = whatToExpectRaw != null ?
                whatToExpectRaw.stream()
                        .map(item -> ActivityBookingResponse.WhatToExpectResponse.builder()
                                .text(item.get("text"))
                                .imageUrl(item.get("image"))
                                .build())
                        .collect(Collectors.toList()) : null;

        List<Map<String, Object>> itineraryRaw = JsonUtils.fromJson(booking.getItinerary(),
                new TypeReference<List<Map<String, Object>>>() {});
        List<ActivityBookingResponse.ItineraryResponse> itinerary = itineraryRaw != null ?
                itineraryRaw.stream()
                        .map(item -> ActivityBookingResponse.ItineraryResponse.builder()
                                .title((String) item.get("title"))
                                .content((String) item.get("content"))
                                .images((List<String>) item.get("images"))
                                .build())
                        .collect(Collectors.toList()) : null;

        return ActivityBookingResponse.builder()
                .id(booking.getId())
                .externalId(booking.getExternalId())
                .source(booking.getSource())
                .url(booking.getUrl())
                .redirectUrl(booking.getRedirectUrl())
                .title(booking.getTitle())
                .description(booking.getDescription())
                .activityAddress(booking.getActivityAddress())
                .departingFrom(booking.getDepartingFrom())
                .destinations(destinations)
                .destinationCoordinates(destinationCoordinates)
                .navigationList(navigationList)
                .itineraryStops(itineraryStops)
                .pickupAddresses(pickupAddresses)
                .priceAmount(convertPrice(booking.getPriceAmount(), booking.getPriceCurrency(), targetCurrency))
                .priceCurrency(resolveDisplayCurrency(booking.getPriceCurrency(), targetCurrency))
                .duration(booking.getDurationRaw())
                .rating(booking.getRating())
                .reviewCount(booking.getReviewCount())
                .bookedCount(booking.getBookedCount())
                .thumbnail(booking.getThumbnail())
                .images(images)
                .highlights(highlights)
                .whatToExpect(whatToExpect)
                .itinerary(itinerary)
                .build();
    }

    private String extractExternalId(String url) {
        String[] parts = url.split("/");
        for (String part : parts) {
            if (part.contains("-") && part.matches("\\d+.*")) {
                return part.split("-")[0];
            }
        }
        return UUID.randomUUID().toString();
    }

    private String extractDepartingFrom(ImportActivityBookingRequest.LocationInfo location) {
        if (location.getNavigationList() != null && !location.getNavigationList().isEmpty()) {
            return location.getNavigationList().get(0);
        }
        if (location.getPickupAddresses() != null && !location.getPickupAddresses().isEmpty()) {
            String pickup = location.getPickupAddresses().get(0);
            if (pickup.contains(" ")) {
                return pickup.split(" ")[0];
            }
            return pickup;
        }
        return "Unknown";
    }

    private BigDecimal parseDuration(String duration) {
        if (duration == null) return BigDecimal.ZERO;

        String[] parts = duration.toLowerCase().split("-");
        if (parts.length > 0) {
            String firstPart = parts[0].trim().replaceAll("[^0-9.]", "");
            try {
                return new BigDecimal(firstPart);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private ActivityBookingResponse updateExisting(ActivityBooking existing, ImportActivityBookingRequest request) {
        String departingFrom = extractDepartingFrom(request.getLocation());
        BigDecimal durationHours = parseDuration(request.getDuration());
        List<String> destinations = normalizeDestinations(request.getDestinations(), request.getLocation());
        List<GeoCoordinateDto> destinationCoordinates = resolveDestinationCoordinates(request);

        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setActivityAddress(request.getLocation().getActivityAddress());
        existing.setDepartingFrom(departingFrom);
        existing.setDestinations(JsonUtils.toJson(destinations));
        existing.setDestinationCoordinates(toJsonOrNull(destinationCoordinates));
        existing.setNavigationList(JsonUtils.toJson(request.getLocation().getNavigationList()));
        existing.setItineraryStops(JsonUtils.toJson(request.getLocation().getItineraryStops()));
        existing.setPickupAddresses(JsonUtils.toJson(request.getLocation().getPickupAddresses()));
        existing.setPriceAmount(request.getPrice().getAmount());
        existing.setDurationRaw(request.getDuration());
        existing.setDurationHours(durationHours);
        existing.setRating(request.getRating());
        existing.setReviewCount(request.getReviewCount());
        existing.setBookedCount(request.getBookedCount());
        existing.setThumbnail(request.getImages() != null && !request.getImages().isEmpty() ? request.getImages().get(0) : null);
        existing.setImages(JsonUtils.toJson(request.getImages()));
        existing.setHighlights(JsonUtils.toJson(request.getHighlights()));
        existing.setWhatToExpect(JsonUtils.toJson(request.getWhatToExpect()));
        existing.setItinerary(JsonUtils.toJson(request.getItinerary()));

        ActivityBookingSearchFieldHelper.apply(existing);
        repository.update(existing);

        try {
            writer.deleteDocuments(new Term("id", existing.getId().toString()));
            indexActivityBooking(existing);
            writer.commit();
            refreshSearchIndex();
        } catch (IOException e) {
            log.error("Failed to reindex activity booking", e);
        }

        log.info("Updated activity booking: {}", existing.getId());
        return mapToResponse(existing);
    }

    private List<String> normalizeDestinations(List<String> destinations, ImportActivityBookingRequest.LocationInfo location) {
        List<String> values = (destinations != null && !destinations.isEmpty())
                ? destinations
                : location != null ? location.getNavigationList() : Collections.emptyList();
        return values == null ? Collections.emptyList()
                : values.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .distinct()
                        .collect(Collectors.toList());
    }

    private List<GeoCoordinateDto> resolveDestinationCoordinates(ImportActivityBookingRequest request) {
        List<GeoCoordinateDto> coordinates = request.getDestinationCoordinates();
        if ((coordinates == null || coordinates.isEmpty()) && request.getLocation() != null) {
            coordinates = request.getLocation().getDestinationCoordinates();
        }
        return normalizeCoordinates(coordinates);
    }

    private List<GeoCoordinateDto> normalizeCoordinates(List<GeoCoordinateDto> coordinates) {
        if (coordinates == null) {
            return null;
        }
        List<GeoCoordinateDto> normalized = coordinates.stream()
                .filter(Objects::nonNull)
                .filter(coordinate -> coordinate.getLat() != null && coordinate.getLng() != null)
                .collect(Collectors.toList());
        return normalized.isEmpty() ? null : normalized;
    }

    private String toJsonOrNull(List<GeoCoordinateDto> coordinates) {
        return coordinates == null || coordinates.isEmpty() ? null : JsonUtils.toJson(coordinates);
    }

    private List<String> normalizeDestinationFilter(List<String> destinations) {
        return DestinationMatchUtils.parseFilterValues(destinations);
    }

    private List<String> toSqlDestinationKeys(List<String> destinationFilters) {
        return destinationFilters.stream()
                .map(DestinationMatchUtils::normalizeKey)
                .filter(key -> key.length() >= DestinationMatchUtils.minSubstringLength())
                .distinct()
                .collect(Collectors.toList());
    }

    private int computeFetchLimit(int page, int size) {
        int safeSize = Math.max(size, 1);
        int safePage = Math.max(page, 0);
        return Math.min(MAX_GEO_FETCH, (safePage + 1) * safeSize + safeSize * 2);
    }

    private ActivityBookingGeoSearchParams buildGeoSearchParams(
            BigDecimal latitude,
            BigDecimal longitude,
            double radiusKm,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minRating,
            int limit,
            int offset) {
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        double latDelta = radiusKm / 111.0d;
        double lngDelta = radiusKm / (111.0d * Math.max(0.1d, Math.cos(Math.toRadians(lat))));

        return ActivityBookingGeoSearchParams.builder()
                .latitude(latitude)
                .longitude(longitude)
                .radiusKm(radiusKm)
                .minLat(lat - latDelta)
                .maxLat(lat + latDelta)
                .minLng(lng - lngDelta)
                .maxLng(lng + lngDelta)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .limit(limit)
                .offset(offset)
                .build();
    }

    private void refreshSearchIndex() {
        if (searcherManager == null) {
            return;
        }
        try {
            searcherManager.maybeRefresh();
        } catch (IOException e) {
            log.warn("Failed to refresh activity booking Lucene searcher: {}", e.getMessage());
        }
    }

    private boolean matchesDestinations(ActivityBooking booking, List<String> filters) {
        List<String> bookingDestinations = JsonUtils.fromJson(
                booking.getDestinations(), new TypeReference<List<String>>() {});
        return DestinationMatchUtils.matches(bookingDestinations, filters);
    }

    /**
     * Geo search already scopes by coordinates. Optional destination names refine results
     * when present on the booking; bookings without destination tags stay included.
     */
    private boolean matchesDestinationsForGeo(ActivityBooking booking, List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        List<String> bookingDestinations = JsonUtils.fromJson(
                booking.getDestinations(), new TypeReference<List<String>>() {});
        if (bookingDestinations == null || bookingDestinations.isEmpty()) {
            return true;
        }
        return DestinationMatchUtils.matches(bookingDestinations, filters);
    }

    @Override
    public ActivityResponse addToTrip(UUID bookingId, AddBookingToTripRequest request, String targetCurrency, UUID userId) {
        ActivityBooking booking = repository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity booking not found"));

        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));

        String effectiveCurrency = (targetCurrency != null && !targetCurrency.isBlank())
                ? targetCurrency.toUpperCase() : trip.getCurrency();

        BigDecimal convertedPrice = convertPrice(booking.getPriceAmount(), booking.getPriceCurrency(), effectiveCurrency);

        // Coordinates are admin-provided booking data. Do not geocode/search here.
        GeoCoordinateDto primaryCoordinate = getPrimaryDestinationCoordinate(booking);
        BigDecimal lat = primaryCoordinate != null ? primaryCoordinate.getLat() : null;
        BigDecimal lng = primaryCoordinate != null ? primaryCoordinate.getLng() : null;

        CreateActivityRequest actReq = CreateActivityRequest.builder()
                .name(booking.getTitle())
                .address(booking.getActivityAddress())
                .lat(lat)
                .lng(lng)
                .dayNumber(request.getDayNumber())
                .startTime(request.getStartTime() != null
                        ? java.time.LocalTime.parse(request.getStartTime()) : null)
                .estimatedCost(convertedPrice)
                .costCurrency(effectiveCurrency)
                .category("activity")
                .notes(request.getNotes())
                .bookingId(booking.getId())
                .bookingSource(booking.getSource() != null ? booking.getSource() : "KLOOK")
                .build();

        return activityService.createActivity(request.getTripId(), actReq, userId);
    }

    private GeoCoordinateDto getPrimaryDestinationCoordinate(ActivityBooking booking) {
        List<GeoCoordinateDto> coords = JsonUtils.fromJson(
                booking.getDestinationCoordinates(), new TypeReference<List<GeoCoordinateDto>>() {});
        if (coords == null || coords.isEmpty()) {
            return null;
        }
        return coords.stream()
                .filter(Objects::nonNull)
                .filter(coordinate -> coordinate.getLat() != null && coordinate.getLng() != null)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal convertPrice(BigDecimal amount, String storedCurrency, String targetCurrency) {
        if (amount == null) return null;
        if (targetCurrency == null || targetCurrency.isBlank()) return amount;
        String stored = (storedCurrency != null) ? storedCurrency : "USD";
        if (stored.equalsIgnoreCase(targetCurrency)) return amount;
        return exchangeRateService.convert(amount, stored, targetCurrency);
    }

    private String resolveDisplayCurrency(String storedCurrency, String targetCurrency) {
        if (targetCurrency != null && !targetCurrency.isBlank()) return targetCurrency.toUpperCase();
        return storedCurrency != null ? storedCurrency : "USD";
    }

    private List<ActivityBooking> applyTextFilter(
            List<ActivityBooking> bookings, String query, Float minLuceneScore) {
        if (query == null || query.trim().isEmpty()) {
            return bookings;
        }
        try {
            return sortBookingsByRelevance(bookings, query.trim(), minLuceneScore);
        } catch (Exception e) {
            log.warn("Failed to rank activities by query: {}", e.getMessage());
            return bookings;
        }
    }

    private List<ActivityBookingResponse> paginateAndMap(
            List<ActivityBooking> bookings, int page, int size, String targetCurrency) {
        return bookings.stream()
                .skip((long) page * size)
                .limit(size)
                .map(b -> mapToResponse(b, targetCurrency))
                .collect(Collectors.toList());
    }

    private List<ActivityBooking> sortBookingsByRelevance(
            List<ActivityBooking> bookings, String query, Float minLuceneScore) throws Exception {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            Query textQuery = LuceneTitleQueryBuilder.build(query, analyzer);

            List<ScoredBooking> scoredBookings = new ArrayList<>();
            for (ActivityBooking booking : bookings) {
                float score = scoreBooking(searcher, textQuery, booking.getId());
                scoredBookings.add(new ScoredBooking(booking, score));
            }

            scoredBookings.sort((left, right) -> Float.compare(right.score, left.score));
            return scoredBookings.stream()
                    .filter(scoredBooking -> minLuceneScore == null || scoredBooking.score >= minLuceneScore)
                    .map(scoredBooking -> scoredBooking.booking)
                    .collect(Collectors.toList());
        } finally {
            searcherManager.release(searcher);
        }
    }

    private float scoreBooking(IndexSearcher searcher, Query textQuery, UUID bookingId) throws IOException {
        TopDocs idHits = searcher.search(new TermQuery(new Term("id", bookingId.toString())), 1);
        if (idHits.scoreDocs.length == 0) {
            return 0f;
        }
        return searcher.explain(textQuery, idHits.scoreDocs[0].doc).getValue().floatValue();
    }

    private static class ScoredBooking {
        private final ActivityBooking booking;
        private final float score;

        private ScoredBooking(ActivityBooking booking, float score) {
            this.booking = booking;
            this.score = score;
        }
    }
}

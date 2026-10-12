package com.ds.goroute.service.impl;

import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.entity.Place;
import com.ds.goroute.mapper.PlaceTranslationMapper;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceSubType;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.ds.goroute.utils.MapTile;
import com.ds.goroute.type.PlaceSearchSort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceSearchIndexServiceImplTest {

    @TempDir
    Path tempDir;

    private PlaceSearchIndexServiceImpl searchService;
    private PlaceRepository placeRepository;
    private PlaceTranslationMapper translationMapper;
    private FoodRepository foodRepository;

    @BeforeEach
    void setUp() throws Exception {
        placeRepository = mock(PlaceRepository.class);
        translationMapper = mock(PlaceTranslationMapper.class);
        foodRepository = mock(FoodRepository.class);
        when(placeRepository.countAll()).thenReturn(0L);
        when(translationMapper.findByPlaceId(any())).thenReturn(List.of());
        when(foodRepository.findFoodTagsByPlaceIds(any())).thenReturn(List.of());

        searchService = startService();
    }

    private PlaceSearchIndexServiceImpl startService() throws Exception {
        PlaceSearchIndexServiceImpl service = new PlaceSearchIndexServiceImpl(placeRepository, translationMapper, foodRepository);
        ReflectionTestUtils.setField(service, "indexPath", tempDir.resolve("places").toString());
        service.init();
        return service;
    }

    /** kill -9: the process goes away without the shutdown commit. */
    private void crash(PlaceSearchIndexServiceImpl service) throws Exception {
        ((ExecutorService) ReflectionTestUtils.getField(service, "reindexExecutor")).shutdownNow();
        ((SearcherManager) ReflectionTestUtils.getField(service, "searcherManager")).close();
        ((IndexWriter) ReflectionTestUtils.getField(service, "writer")).rollback();
        ((Directory) ReflectionTestUtils.getField(service, "directory")).close();
    }

    private void awaitReindex(PlaceSearchIndexServiceImpl service) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (service.isReindexRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        searchService.cleanup();
    }

    @Test
    void combinesGeoRadiusAndCategoryInOneSearch() throws Exception {
        Place nearbyCafe = place("Nearby cafe", "cafe", 10.0001, 106.0);
        Place nearbyMuseum = place("Nearby museum", "museum", 10.00005, 106.0);
        Place distantCafe = place("Distant cafe", "cafe", 10.1, 106.0);
        searchService.indexPlace(nearbyCafe);
        searchService.indexPlace(nearbyMuseum);
        searchService.indexPlace(distantCafe);

        PlaceSearchCriteria criteria = criteria(null, "CAFE", 2.0);

        assertThat(searchService.searchPlaceIds(criteria)).containsExactly(nearbyCafe.getId());
    }

    @Test
    void foldsVietnameseAccentsForTextSearch() throws Exception {
        Place phoThin = place("Phở Thìn Bờ Hồ", "restaurant", 10.0001, 106.0);
        searchService.indexPlace(phoThin);

        assertThat(searchService.searchPlaceIds(criteria("pho thin", null, 2.0)))
                .containsExactly(phoThin.getId());
    }

    @Test
    void subTypeFilterMatchesAnyOfTheChosenKinds() throws Exception {
        Place halalSeafood = place("Halal seafood", "restaurant", 10.0001, 106.0);
        halalSeafood.setSubTypes(new String[] {"SEAFOOD", "HALAL"});
        Place vegetarian = place("Quán chay", "restaurant", 10.0002, 106.0);
        vegetarian.setSubTypes(new String[] {"VEGETARIAN"});
        Place plain = place("Plain restaurant", "restaurant", 10.0003, 106.0);
        searchService.indexPlace(halalSeafood);
        searchService.indexPlace(vegetarian);
        searchService.indexPlace(plain);

        assertThat(searchService.searchPlaceIds(subTypeCriteria(List.of("halal", "VEGETARIAN"))))
                .containsExactlyInAnyOrder(halalSeafood.getId(), vegetarian.getId());
        assertThat(searchService.searchPlaceIds(subTypeCriteria(List.of("SEAFOOD"))))
                .containsExactly(halalSeafood.getId());
    }

    @Test
    void otherMatchesPlacesWithoutAnyKindAlongsideTheChosenOnes() throws Exception {
        Place halal = place("Halal", "restaurant", 10.0001, 106.0);
        halal.setSubTypes(new String[] {"HALAL"});
        Place cafe = place("Cafe", "restaurant", 10.0002, 106.0);
        cafe.setSubTypes(new String[] {"CAFE"});
        Place plain = place("Plain restaurant", "restaurant", 10.0003, 106.0);
        searchService.indexPlace(halal);
        searchService.indexPlace(cafe);
        searchService.indexPlace(plain);

        assertThat(searchService.searchPlaceIds(subTypeCriteria(List.of("other"))))
                .containsExactly(plain.getId());
        assertThat(searchService.searchPlaceIds(subTypeCriteria(List.of("HALAL", "OTHER"))))
                .containsExactlyInAnyOrder(halal.getId(), plain.getId());
    }

    @Test
    void countsEachKindInTheAreaAndThePlacesWithNone() throws Exception {
        Place halalSeafood = place("Halal seafood", "restaurant", 10.0001, 106.0);
        halalSeafood.setSubTypes(new String[] {"SEAFOOD", "HALAL"});
        Place seafood = place("Seafood", "restaurant", 10.0002, 106.0);
        seafood.setSubTypes(new String[] {"SEAFOOD"});
        Place plain = place("Plain restaurant", "restaurant", 10.0003, 106.0);
        Place farSeafood = place("Far seafood", "restaurant", 11.0, 106.0);
        farSeafood.setSubTypes(new String[] {"SEAFOOD"});
        for (Place place : List.of(halalSeafood, seafood, plain, farSeafood)) {
            searchService.indexPlace(place);
        }

        // The area's own sub-type filter is ignored: the counts are for choosing one.
        Map<String, Integer> counts = searchService.countBySubType(
                subTypeCriteria(List.of("HALAL")),
                List.of(PlaceSubType.SEAFOOD, PlaceSubType.HALAL, PlaceSubType.CAFE));

        assertThat(counts).containsExactly(
                Map.entry("SEAFOOD", 2),
                Map.entry("HALAL", 1),
                Map.entry("CAFE", 0),
                Map.entry(PlaceSubType.UNTYPED_FILTER, 1));
    }

    private PlaceSearchCriteria subTypeCriteria(List<String> subTypes) {
        return new PlaceSearchCriteria(null, BigDecimal.valueOf(10.0), BigDecimal.valueOf(106.0),
                BigDecimal.valueOf(2.0), null, null, subTypes, null, null, null, null,
                false, PlaceSearchSort.RELEVANCE, null, 0, 20);
    }

    @Test
    void ranksByDistanceWhenKeywordIsAbsent() throws Exception {
        Place farther = place("Farther", "cafe", 10.01, 106.0);
        Place nearer = place("Nearer", "cafe", 10.0001, 106.0);
        searchService.indexPlace(farther);
        searchService.indexPlace(nearer);

        assertThat(searchService.searchPlaceIds(criteria(null, null, 5.0)))
                .containsExactly(nearer.getId(), farther.getId());
    }

    @Test
    void exactPhraseCanOutrankACloserPartialMatch() throws Exception {
        Place closerPartial = place("Pho Something Thin", "restaurant", 10.0001, 106.0);
        Place fartherExact = place("Pho Thin", "restaurant", 10.02, 106.0);
        searchService.indexPlace(closerPartial);
        searchService.indexPlace(fartherExact);

        assertThat(searchService.searchPlaceIds(criteria("pho thin", null, 5.0)))
                .containsExactly(fartherExact.getId(), closerPartial.getId());
    }

    @Test
    void sortsOnlyByCalculatedRatingBeforePaginating() throws Exception {
        Place lowRated = place("Low rated", "cafe", 10.0001, 106.0, 4.9, 3.5);
        Place topRated = place("Top rated", "cafe", 10.0002, 106.0, 3.5, 4.9);
        Place middleRated = place("Middle rated", "cafe", 10.0003, 106.0, 4.4, 4.4);
        Place noCalculatedRating = place("No calculated", "cafe", 10.0004, 106.0, 4.8);
        searchService.indexPlace(lowRated);
        searchService.indexPlace(topRated);
        searchService.indexPlace(middleRated);
        searchService.indexPlace(noCalculatedRating);

        assertThat(searchService.searchPlaceIds(ratingCriteria(0, 2)))
                .containsExactly(topRated.getId(), middleRated.getId());
        assertThat(searchService.searchPlaceIds(ratingCriteria(1, 2)))
                .containsExactly(lowRated.getId(), noCalculatedRating.getId());
    }

    @Test
    void ratingChangeReordersRatingSortWithoutWaitingForACommit() throws Exception {
        Place first = place("First", "cafe", 10.0001, 106.0, 4.5, 4.8);
        Place second = place("Second", "cafe", 10.0002, 106.0, 4.5, 4.0);
        searchService.indexPlace(first);
        searchService.indexPlace(second);
        assertThat(searchService.searchPlaceIds(ratingCriteria(0, 2)))
                .containsExactly(first.getId(), second.getId());

        second.setAdjustedRating(BigDecimal.valueOf(4.9));
        searchService.indexPlace(second);

        assertThat(searchService.searchPlaceIds(ratingCriteria(0, 2)))
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void nameSearchNeedsNoLocationFoldsAccentsAndHidesInactivePlaces() throws Exception {
        Place active = place("Bún Chả Hương Liên", "restaurant", 21.0, 105.8);
        Place inactive = place("Bún Chả Đắc Kim", "restaurant", 10.0, 106.0);
        inactive.setVisibilityStatus(PlaceVisibilityStatus.INACTIVE);
        searchService.indexPlace(active);
        searchService.indexPlace(inactive);

        assertThat(searchService.searchPlaceIdsByName("bun cha", null, false, 0, 20))
                .containsExactly(active.getId());
        assertThat(searchService.searchPlaceIdsByName("bun cha", null, true, 0, 20))
                .containsExactlyInAnyOrder(active.getId(), inactive.getId());
    }

    @Test
    void reindexRunsInTheBackgroundAndReplacesStaleDocuments() throws Exception {
        Place stale = place("Stale cafe", "cafe", 10.0001, 106.0);
        searchService.indexPlace(stale);
        Place fresh = place("Fresh cafe", "cafe", 10.0002, 106.0);
        CountDownLatch release = new CountDownLatch(1);
        when(placeRepository.findSearchIndexPage(any(), anyInt())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return List.of(fresh);
        });

        assertThat(searchService.triggerReindex()).isTrue();
        // A second trigger while the first runs starts nothing, and the old index still answers.
        assertThat(searchService.triggerReindex()).isFalse();
        assertThat(searchService.searchPlaceIds(criteria(null, null, 2.0))).containsExactly(stale.getId());

        release.countDown();
        long deadline = System.currentTimeMillis() + 10_000;
        while (searchService.isReindexRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertThat(searchService.isReindexRunning()).isFalse();
        assertThat(searchService.searchPlaceIds(criteria(null, null, 2.0))).containsExactly(fresh.getId());
        verify(placeRepository, never()).findPage(anyInt(), anyInt());
    }

    @Test
    void aPlaceWrittenDuringAReindexSurvivesTheSwap() throws Exception {
        Place existing = place("Existing cafe", "cafe", 10.0001, 106.0);
        Place addedMeanwhile = place("Added cafe", "cafe", 10.0003, 106.0);
        CountDownLatch release = new CountDownLatch(1);
        when(placeRepository.findSearchIndexPage(any(), anyInt())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return List.of(existing);
        });
        when(placeRepository.findSearchIndexRow(addedMeanwhile.getId())).thenReturn(Optional.of(addedMeanwhile));

        searchService.triggerReindex();
        searchService.indexPlace(addedMeanwhile);
        release.countDown();
        long deadline = System.currentTimeMillis() + 10_000;
        while (searchService.isReindexRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertThat(searchService.searchPlaceIds(criteria(null, null, 2.0)))
                .containsExactlyInAnyOrder(existing.getId(), addedMeanwhile.getId());
    }

    @Test
    void writesLostToACrashBeforeTheBatchedCommitAreRebuiltAtStartup() throws Exception {
        Place written = place("Uncommitted cafe", "cafe", 10.0001, 106.0);
        searchService.indexPlace(written);
        crash(searchService);
        when(placeRepository.findSearchIndexPage(any(), anyInt())).thenReturn(List.of(written));

        searchService = startService();
        awaitReindex(searchService);

        assertThat(searchService.searchPlaceIds(criteria(null, null, 2.0))).containsExactly(written.getId());
        assertThat(tempDir.resolve("places.uncommitted")).doesNotExist();
    }

    @Test
    void aCommittedIndexKeepsServingWhileTheStartupRefreshRebuildsItFromTheDatabase() throws Exception {
        Place written = place("Committed cafe", "cafe", 10.0001, 106.0);
        searchService.indexPlace(written);
        searchService.commitPendingChanges();
        assertThat(tempDir.resolve("places.uncommitted")).doesNotExist();
        crash(searchService);
        // While the app was down the database changed behind its back (a migration, a manual fix).
        Place fresh = place("Fresh cafe", "cafe", 10.0002, 106.0);
        CountDownLatch release = new CountDownLatch(1);
        when(placeRepository.findSearchIndexPage(any(), anyInt())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return List.of(fresh);
        });

        searchService = startService();

        assertThat(searchService.isReindexRunning()).isTrue();
        assertThat(searchService.searchPlaceIds(criteria(null, null, 2.0))).containsExactly(written.getId());
        release.countDown();
        awaitReindex(searchService);
        assertThat(searchService.searchPlaceIds(criteria(null, null, 2.0))).containsExactly(fresh.getId());
    }

    @Test
    void aRecoveryRebuildKeepsTheMarkerAcrossPeriodicCommitsUntilItSwapsIn() throws Exception {
        Place written = place("Lost cafe", "cafe", 10.0001, 106.0);
        searchService.indexPlace(written);
        crash(searchService);
        CountDownLatch release = new CountDownLatch(1);
        when(placeRepository.findSearchIndexPage(any(), anyInt())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return List.of(written);
        });

        searchService = startService();
        searchService.commitPendingChanges();

        assertThat(tempDir.resolve("places.uncommitted")).exists();
        release.countDown();
        awaitReindex(searchService);
        assertThat(tempDir.resolve("places.uncommitted")).doesNotExist();
    }

    @Test
    void mapTileKeepsTheMostProminentPlaceOfEachCell() throws Exception {
        MapTile tile = MapTile.containing(21.0287, 105.8524, 12);
        double cornerLat = tile.north() - 0.0005;
        double cornerLng = tile.west() + 0.0005;
        Place landmark = mapPlace("Landmark", cornerLat, cornerLng, 4.6, 30_000);
        Place nextDoor = mapPlace("Next door", cornerLat - 0.0001, cornerLng + 0.0001, 4.9, 12);
        Place farCorner = mapPlace("Far corner", tile.south() + 0.0005, tile.east() - 0.0005, 4.0, 50);
        searchService.indexPlace(landmark);
        searchService.indexPlace(nextDoor);
        searchService.indexPlace(farCorner);

        Map<MapTile, List<UUID>> found = searchService.findMapTilePlaceIds(List.of(tile), null, null);

        assertThat(found.get(tile)).containsExactly(landmark.getId(), farCorner.getId());
    }

    @Test
    void mapTileKeepsThePlaceTravellersUseOverTheMostReviewed() throws Exception {
        MapTile tile = MapTile.containing(21.0287, 105.8524, 12);
        double cornerLat = tile.north() - 0.0005;
        double cornerLng = tile.west() + 0.0005;
        Place reviewed = mapPlace("Reviewed", cornerLat, cornerLng, 4.9, 500_000);
        Place used = mapPlace("Used", cornerLat - 0.0001, cornerLng + 0.0001, 3.2, 40);
        used.setTripUseCount(2);
        used.setCheckinCount(1);
        searchService.indexPlace(reviewed);
        searchService.indexPlace(used);

        Map<MapTile, List<UUID>> found = searchService.findMapTilePlaceIds(List.of(tile), null, null);

        assertThat(found.get(tile)).containsExactly(used.getId());
    }

    @Test
    void popularitySortRanksByTripsAndCheckinsNotStars() throws Exception {
        Place starred = place("Starred cafe", "cafe", 10.0, 106.0, 5.0, 5.0);
        Place visited = place("Visited cafe", "cafe", 10.0005, 106.0005, 3.0, 3.0);
        visited.setCheckinCount(4);
        Place planned = place("Planned cafe", "cafe", 10.001, 106.001, 4.0, 4.0);
        planned.setTripUseCount(1);
        searchService.indexPlace(starred);
        searchService.indexPlace(visited);
        searchService.indexPlace(planned);

        List<UUID> ids = searchService.searchPlaceIds(new PlaceSearchCriteria(
                null, BigDecimal.valueOf(10.0), BigDecimal.valueOf(106.0), BigDecimal.valueOf(5.0),
                null, null, null, null, null, null, null, false, PlaceSearchSort.POPULARITY, null, 0, 10));

        assertThat(ids).containsExactly(visited.getId(), planned.getId(), starred.getId());
    }

    @Test
    void aPlaceKeptOnATileIsKeptOnTheCloserTileItFallsIn() throws Exception {
        Random random = new Random(42);
        Map<UUID, Place> placesById = new HashMap<>();
        for (int index = 0; index < 120; index++) {
            Place place = mapPlace("Place " + index,
                    21.0 + random.nextDouble() * 0.08,
                    105.8 + random.nextDouble() * 0.08,
                    3.0 + random.nextDouble() * 2.0,
                    random.nextInt(5_000));
            placesById.put(place.getId(), place);
            searchService.indexPlace(place);
        }

        for (int zoom = 9; zoom <= 14; zoom++) {
            MapTile tile = MapTile.containing(21.04, 105.84, zoom);
            List<UUID> kept = searchService.findMapTilePlaceIds(List.of(tile), null, null).get(tile);
            for (UUID id : kept) {
                Place place = placesById.get(id);
                MapTile closer = MapTile.containing(
                        place.getLatitude().doubleValue(), place.getLongitude().doubleValue(), zoom + 1);
                assertThat(searchService.findMapTilePlaceIds(List.of(closer), null, null).get(closer))
                        .as("zoom %d keeps %s", zoom, place.getTitle())
                        .contains(id);
            }
        }
    }

    @Test
    void mapTileLeavesOutHiddenPlacesAndOtherGroups() throws Exception {
        MapTile tile = MapTile.containing(21.0287, 105.8524, 12);
        Place sight = mapPlace("Sight", tile.north() - 0.0005, tile.west() + 0.0005, 4.5, 900);
        Place hidden = mapPlace("Hidden", tile.south() + 0.0005, tile.east() - 0.0005, 4.5, 900);
        hidden.setVisibilityStatus(PlaceVisibilityStatus.INACTIVE);
        Place restaurant = mapPlace("Restaurant", tile.south() + 0.0005, tile.west() + 0.0005, 4.5, 900);
        restaurant.setPlaceGroup(PlaceGroup.FOOD_AND_DRINK);
        searchService.indexPlace(sight);
        searchService.indexPlace(hidden);
        searchService.indexPlace(restaurant);

        Map<MapTile, List<UUID>> found = searchService.findMapTilePlaceIds(
                List.of(tile), List.of(PlaceGroup.ATTRACTIONS.name()), null);

        assertThat(found.get(tile)).containsExactly(sight.getId());
    }

    @Test
    void mapTilesAnswerEveryTileAskedAndNoOther() throws Exception {
        MapTile asked = MapTile.containing(21.0287, 105.8524, 12);
        MapTile neighbour = new MapTile(12, asked.x() + 1, asked.y());
        MapTile empty = new MapTile(12, asked.x() - 1, asked.y());
        Place here = mapPlace("Here", asked.north() - 0.0005, asked.west() + 0.0005, 4.5, 900);
        Place there = mapPlace("There", neighbour.north() - 0.0005, neighbour.west() + 0.0005, 4.5, 900);
        searchService.indexPlace(here);
        searchService.indexPlace(there);

        Map<MapTile, List<UUID>> found = searchService.findMapTilePlaceIds(List.of(asked, empty), null, null);

        assertThat(found).containsOnlyKeys(asked, empty);
        assertThat(found.get(asked)).containsExactly(here.getId());
        assertThat(found.get(empty)).isEmpty();
    }

    private Place mapPlace(String title, double latitude, double longitude, double rating, int reviews) {
        Place place = place(title, "tourist_attraction", latitude, longitude, rating);
        place.setPlaceGroup(PlaceGroup.ATTRACTIONS);
        place.setReviewCount(reviews);
        return place;
    }

    private PlaceSearchCriteria criteria(String keyword, String category, double radiusKm) {
        return new PlaceSearchCriteria(
                keyword,
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(106.0),
                BigDecimal.valueOf(radiusKm),
                category,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                PlaceSearchSort.RELEVANCE,
                null,
                0,
                20);
    }

    private PlaceSearchCriteria ratingCriteria(int page, int size) {
        return new PlaceSearchCriteria(
                null,
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(106.0),
                BigDecimal.valueOf(5.0),
                "cafe",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                PlaceSearchSort.RATING,
                null,
                page,
                size);
    }

    private Place place(String title, String category, double latitude, double longitude) {
        return place(title, category, latitude, longitude, 4.5);
    }

    private Place place(
            String title,
            String category,
            double latitude,
            double longitude,
            double reviewRating) {
        return place(title, category, latitude, longitude, reviewRating, null);
    }

    private Place place(
            String title,
            String category,
            double latitude,
            double longitude,
            double reviewRating,
            Double adjustedRating) {
        return Place.builder()
                .id(UUID.randomUUID())
                .title(title)
                .category(category)
                .placeGroup(PlaceGroup.FOOD_AND_DRINK)
                .latitude(BigDecimal.valueOf(latitude))
                .longitude(BigDecimal.valueOf(longitude))
                .destinations("[\"hanoi\"]")
                .reviewRating(BigDecimal.valueOf(reviewRating))
                .adjustedRating(
                        adjustedRating == null ? null : BigDecimal.valueOf(adjustedRating))
                .visibilityStatus(PlaceVisibilityStatus.ACTIVE)
                .build();
    }
}

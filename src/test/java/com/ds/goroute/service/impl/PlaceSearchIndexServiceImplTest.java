package com.ds.goroute.service.impl;

import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.entity.Place;
import com.ds.goroute.mapper.PlaceTranslationMapper;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceSearchIndexServiceImplTest {

    @TempDir
    Path tempDir;

    private PlaceSearchIndexServiceImpl searchService;

    @BeforeEach
    void setUp() throws Exception {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        PlaceTranslationMapper translationMapper = mock(PlaceTranslationMapper.class);
        FoodRepository foodRepository = mock(FoodRepository.class);
        when(placeRepository.countAll()).thenReturn(0L);
        when(translationMapper.findByPlaceId(any())).thenReturn(List.of());
        when(foodRepository.findFoodTagsByPlaceIds(any())).thenReturn(List.of());

        searchService = new PlaceSearchIndexServiceImpl(placeRepository, translationMapper, foodRepository);
        ReflectionTestUtils.setField(searchService, "indexPath", tempDir.resolve("places").toString());
        searchService.init();
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
                false,
                false,
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
                false,
                true,
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

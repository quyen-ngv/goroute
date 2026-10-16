package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.ds.goroute.type.TranslationLocale;
import com.ds.goroute.type.PlaceSearchSort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceServiceImplListTest {

    @Mock private PlaceRepository placeRepository;
    @Mock private PlaceSearchIndexService placeSearchIndexService;
    @Mock private PlaceTranslationService placeTranslationService;
    @Mock private FoodRepository foodRepository;
    @Mock private PlaceReviewRepository placeReviewRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private PlaceServiceImpl service;

    @Test
    void guestListIsCappedAtFiftyNewestFirstAndActiveOnly() {
        when(placeRepository.findListPage(any(), eq(true), eq(50), eq(0))).thenReturn(List.of(place("A")));

        List<PlaceResponse> result = service.getAllPlaces(null, null, false, 0, 500);

        assertThat(result).extracting(PlaceResponse::getTitle).containsExactly("A");
        verify(placeRepository).findListPage(null, true, 50, 0);
    }

    @Test
    void adminListKeepsTheConsoleCeilingAndInactivePlaces() {
        service.getAllPlaces("", null, true, 0, 500);

        verify(placeRepository).findListPage(null, false, 500, 0);
    }

    @Test
    void oneCharacterSearchReturnsNothingWithoutQuerying() throws Exception {
        assertThat(service.getAllPlaces("a", null, false, 0, 20)).isEmpty();
        verify(placeSearchIndexService, never()).searchPlaceIdsByName(any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void uuidSearchIsAnExactIdLookup() throws Exception {
        Place place = place("By id");
        when(placeRepository.findListSummaryByIds(List.of(place.getId()), true)).thenReturn(List.of(place));

        List<PlaceResponse> result = service.getAllPlaces(place.getId().toString(), null, false, 0, 20);

        assertThat(result).extracting(PlaceResponse::getId).containsExactly(place.getId());
        verify(placeSearchIndexService, never()).searchPlaceIdsByName(any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void googlePlaceIdIsMatchedExactly() throws Exception {
        Place place = place("By Google id");
        when(placeRepository.findByPlaceId("ChIJabc")).thenReturn(place);
        when(placeRepository.findListSummaryByIds(List.of(place.getId()), true)).thenReturn(List.of(place));

        assertThat(service.getAllPlaces("ChIJabc", null, false, 0, 20))
                .extracting(PlaceResponse::getId).containsExactly(place.getId());
        verify(placeSearchIndexService, never()).searchPlaceIdsByName(any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void textGoesThroughLuceneAndKeepsItsOrder() throws Exception {
        Place first = place("Phở Thìn");
        Place second = place("Phở Bát Đàn");
        when(placeSearchIndexService.searchPlaceIdsByName("pho", null, false, 0, 20))
                .thenReturn(List.of(first.getId(), second.getId()));
        when(placeRepository.findListSummaryByIds(anyList(), eq(true))).thenReturn(List.of(second, first));

        List<PlaceResponse> result = service.getAllPlaces("pho", null, false, 0, 20);

        assertThat(result).extracting(PlaceResponse::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void aPageOfPlacesLoadsTranslationsOnceAndSendsOnlyTheResolvedLocale() {
        Place first = place("Hồ Gươm");
        Place second = place("Văn Miếu");
        when(placeRepository.findListPage(any(), anyBoolean(), anyInt(), anyInt())).thenReturn(List.of(first, second));
        when(placeTranslationService.findByPlaceIds(anyList())).thenReturn(Map.of(
                first.getId(), List.of(
                        translation(first.getId(), TranslationLocale.VI, "Hồ Gươm"),
                        translation(first.getId(), TranslationLocale.EN, "Sword Lake"))));

        List<PlaceResponse> result = service.getAllPlaces(null, null, false, 0, 20);

        verify(placeTranslationService, times(1)).findByPlaceIds(anyList());
        verify(placeTranslationService, never()).resolve(any(), anyString());
        verify(placeTranslationService, never()).allResponses(any());
        // No Accept-Language means English: only that translation travels in a list.
        assertThat(result.get(0).getTranslations()).containsOnlyKeys("en");
        assertThat(result.get(0).getTitle()).isEqualTo("Sword Lake");
        assertThat(result.get(1).getTranslations()).isEmpty();
        assertThat(result.get(1).getTitle()).isEqualTo("Văn Miếu");
    }

    @Test
    void searchHydratesFromTheListProjectionNotTheFullRow() throws Exception {
        Place place = place("Nearby");
        when(placeSearchIndexService.searchPlaceIds(any())).thenReturn(List.of(place.getId()));
        when(placeRepository.findListSummaryByIds(List.of(place.getId()), true)).thenReturn(List.of(place));

        List<PlaceResponse> result = service.searchPlaces(null, BigDecimal.TEN, BigDecimal.valueOf(106),
                BigDecimal.ONE, null, null, null, null, PlaceSearchSort.RELEVANCE, null, null, null, false, null, 0, 20);

        assertThat(result).hasSize(1);
        verify(placeRepository, never()).findByIds(any());
        verify(placeTranslationService, times(1)).findByPlaceIds(anyList());
    }

    @Test
    void sharedSearchResultsAreReMeasuredFromEachCallersOwnPoint() {
        PlaceResponse cached = PlaceResponse.builder()
                .title("Hoan Kiem")
                .latitude(new BigDecimal("21.0287"))
                .longitude(new BigDecimal("105.8524"))
                .distance(0.0)
                .build();
        PlaceResponse noPoint = PlaceResponse.builder().title("Unknown").build();
        List<PlaceResponse> cacheEntry = List.of(cached, noPoint);

        List<PlaceResponse> forCaller = PlaceService.withDistanceFrom(
                cacheEntry, new BigDecimal("21.0377"), new BigDecimal("105.8524"));

        assertThat(forCaller).extracting(PlaceResponse::getTitle).containsExactly("Hoan Kiem", "Unknown");
        assertThat(forCaller.get(0).getDistance()).isCloseTo(1.0, offset(0.01));
        assertThat(forCaller.get(1).getDistance()).isEqualTo(Double.MAX_VALUE);
        assertThat(cached.getDistance()).as("the cached entry is not modified").isEqualTo(0.0);
    }

    @Test
    void movingAPlaceReassignsItsWardEvenWhenItHadOne() {
        Place place = place("Moved");
        place.setWardCode("00001");
        place.setLatitude(BigDecimal.TEN);
        place.setLongitude(BigDecimal.valueOf(106));
        when(placeRepository.findById(place.getId())).thenReturn(Optional.of(place));
        when(placeRepository.assignWard(place.getId())).thenReturn(1);
        when(placeRepository.findGeoCodes(place.getId())).thenReturn(Optional.of(
                Place.builder().id(place.getId()).wardCode("00002").provinceCode("79").build()));

        PlaceResponse response = service.updatePlace(place.getId(), UpdatePlaceRequest.builder()
                .title("Moved").latitude(new BigDecimal("10.5")).longitude(BigDecimal.valueOf(106)).build());

        verify(placeRepository).assignWard(place.getId());
        assertThat(response.getWardCode()).isEqualTo("00002");
        assertThat(response.getProvinceCode()).isEqualTo("79");
    }

    @Test
    void anUnmovedPlaceKeepsItsWard() {
        Place place = place("Still");
        place.setWardCode("00001");
        place.setLatitude(new BigDecimal("10.0"));
        place.setLongitude(BigDecimal.valueOf(106));
        when(placeRepository.findById(place.getId())).thenReturn(Optional.of(place));

        service.updatePlace(place.getId(), UpdatePlaceRequest.builder()
                .title("Still").latitude(new BigDecimal("10.000")).longitude(BigDecimal.valueOf(106)).build());

        verify(placeRepository, never()).assignWard(any());
    }

    @Test
    void anUpdateWithoutTimezoneOrAttributesKeepsTheStoredValues() {
        Place place = place("Kept");
        place.setTimezone("Asia/Ho_Chi_Minh");
        place.setAttributes("{\"free_entry\":{\"value\":true}}");
        when(placeRepository.findById(place.getId())).thenReturn(Optional.of(place));

        service.updatePlace(place.getId(), UpdatePlaceRequest.builder()
                .title("Kept").latitude(BigDecimal.TEN).longitude(BigDecimal.valueOf(106)).build());

        assertThat(place.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(place.getAttributes()).isEqualTo("{\"free_entry\":{\"value\":true}}");
    }

    @Test
    void anExplicitNullAttributesClearsThem() {
        Place place = place("Cleared");
        place.setAttributes("{\"free_entry\":{\"value\":true}}");
        when(placeRepository.findById(place.getId())).thenReturn(Optional.of(place));

        service.updatePlace(place.getId(), UpdatePlaceRequest.builder()
                .title("Cleared").latitude(BigDecimal.TEN).longitude(BigDecimal.valueOf(106))
                .attributes(NullNode.getInstance()).build());

        assertThat(place.getAttributes()).isNull();
    }

    @Test
    void placeReviewsCheckExistenceWithoutLoadingThePlace() {
        UUID placeId = UUID.randomUUID();
        when(placeRepository.existsById(placeId)).thenReturn(true);

        service.getPlaceReviews(placeId, 0, 20);

        verify(placeRepository, never()).findById(any());
    }

    private static Place place(String title) {
        return Place.builder()
                .id(UUID.randomUUID())
                .title(title)
                .placeGroup(PlaceGroup.FOOD_AND_DRINK)
                .latitude(BigDecimal.TEN)
                .longitude(BigDecimal.valueOf(106))
                .visibilityStatus(PlaceVisibilityStatus.ACTIVE)
                .build();
    }

    private static PlaceTranslation translation(UUID placeId, TranslationLocale locale, String name) {
        return PlaceTranslation.builder().placeId(placeId).locale(locale).name(name).build();
    }
}

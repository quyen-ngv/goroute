package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.GroupedPlaceSearchResponse;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.ProvinceNameResolver;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PlaceSearchSort;
import com.ds.goroute.utils.LuceneKeywordQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceGroupedSearchServiceImplTest {

    private static final BigDecimal SAIGON_LAT = new BigDecimal("10.7769");
    private static final BigDecimal SAIGON_LNG = new BigDecimal("106.7009");
    private static final BigDecimal RADIUS = new BigDecimal("30");

    private PlaceService placeService;
    private BusinessConfigService config;
    private ProvinceNameResolver provinces;
    private PlaceGroupedSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        placeService = mock(PlaceService.class);
        config = mock(BusinessConfigService.class);
        provinces = mock(ProvinceNameResolver.class);
        when(config.getBoolean(BusinessConfigKey.PLACE_SEARCH_ELSEWHERE_ENABLED)).thenReturn(true);
        when(config.getInt(BusinessConfigKey.PLACE_SEARCH_ELSEWHERE_MAX)).thenReturn(10);
        when(provinces.nameOf("01")).thenReturn("Hà Nội");
        when(provinces.nameOf("79")).thenReturn("Hồ Chí Minh");
        service = new PlaceGroupedSearchServiceImpl(placeService, config, provinces);
    }

    @Test
    void searchesElsewhereWhenTheAreaFindsNothing() {
        nearby(List.of());
        PlaceResponse hanoi = place("Phở Thìn Bờ Hồ", "01", 21.0285, 105.8542);
        elsewhere(List.of(hanoi));

        GroupedPlaceSearchResponse result = search("phở thìn", 0);

        assertThat(result.getNearby()).isEmpty();
        assertThat(result.getElsewhere()).singleElement().satisfies(place -> {
            assertThat(place.getId()).isEqualTo(hanoi.getId());
            assertThat(place.getProvinceName()).isEqualTo("Hà Nội");
            assertThat(place.getDistance()).isBetween(1_100.0, 1_200.0);
        });
        assertThat(result.isElsewhereTruncated()).isFalse();
        verify(placeService).searchPlacesByNameOutside("phở thìn", SAIGON_LAT, SAIGON_LNG, RADIUS, null,
                10 * PlaceGroupedSearchServiceImpl.CANDIDATES_PER_KEPT);
    }

    @Test
    void searchesElsewhereWhenTheAreaMatchesOnlyOneWord() {
        nearby(List.of(place("Phở Minh", "79", 10.7770, 106.7010)));
        when(placeService.areaKeywordTier("phở thìn", SAIGON_LAT, SAIGON_LNG, RADIUS, null))
                .thenReturn(LuceneKeywordQuery.Match.ANY_WORD);
        elsewhere(List.of(place("Phở Thìn Bờ Hồ", "01", 21.0285, 105.8542)));

        GroupedPlaceSearchResponse result = search("phở thìn", 0);

        assertThat(result.getNearby()).extracting(PlaceResponse::getTitle).containsExactly("Phở Minh");
        assertThat(result.getNearby().get(0).getProvinceName()).isEqualTo("Hồ Chí Minh");
        assertThat(result.getElsewhere()).extracting(PlaceResponse::getTitle).containsExactly("Phở Thìn Bờ Hồ");
    }

    @Test
    void staysInTheAreaWhenItMatchesEveryWord() {
        nearby(List.of(place("Phở Thìn", "79", 10.7770, 106.7010)));
        when(placeService.areaKeywordTier(any(), any(), any(), any(), any()))
                .thenReturn(LuceneKeywordQuery.Match.EXACT);

        GroupedPlaceSearchResponse result = search("phở thìn", 0);

        assertThat(result.getElsewhere()).isEmpty();
        verify(placeService, never()).searchPlacesByNameOutside(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void neverSearchesElsewhereOnLaterPagesWithoutAKeywordOrWhenSwitchedOff() {
        nearby(List.of());

        assertThat(search("phở thìn", 1).getElsewhere()).isEmpty();
        assertThat(search(null, 0).getElsewhere()).isEmpty();
        assertThat(search("  ", 0).getElsewhere()).isEmpty();
        when(config.getBoolean(BusinessConfigKey.PLACE_SEARCH_ELSEWHERE_ENABLED)).thenReturn(false);
        assertThat(search("phở thìn", 0).getElsewhere()).isEmpty();

        verify(placeService, never()).searchPlacesByNameOutside(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void neverSearchesElsewhereWhenTheQueryFiltersBeyondPlaceGroups() {
        when(placeService.searchPlaces(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.search("phở", SAIGON_LAT, SAIGON_LNG, RADIUS, null, null, List.of("PHO"), null,
                PlaceSearchSort.RELEVANCE, null, null, null, false, null, 0, 20);
        service.search("phở", SAIGON_LAT, SAIGON_LNG, RADIUS, null, null, null, new BigDecimal("4"),
                PlaceSearchSort.RELEVANCE, null, null, null, false, null, 0, 20);
        service.search("phở", SAIGON_LAT, SAIGON_LNG, RADIUS, null, null, null, null,
                PlaceSearchSort.RELEVANCE, "hanoi", null, null, false, null, 0, 20);

        verify(placeService, never()).searchPlacesByNameOutside(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void aFailureElsewhereKeepsTheAreaResults() {
        PlaceResponse local = place("Phở Minh", "79", 10.7770, 106.7010);
        nearby(List.of(local));
        when(placeService.areaKeywordTier(any(), any(), any(), any(), any()))
                .thenReturn(LuceneKeywordQuery.Match.ANY_WORD);
        when(placeService.searchPlacesByNameOutside(any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Place search failed"));

        GroupedPlaceSearchResponse result = search("phở thìn", 0);

        assertThat(result.getNearby()).extracting(PlaceResponse::getId).containsExactly(local.getId());
        assertThat(result.getElsewhere()).isEmpty();
        assertThat(result.isElsewhereTruncated()).isFalse();
    }

    @Test
    void foldsAChainToOneRowPerProvinceAndReportsTheCut() {
        nearby(List.of());
        when(config.getInt(BusinessConfigKey.PLACE_SEARCH_ELSEWHERE_MAX)).thenReturn(2);
        List<PlaceResponse> far = new ArrayList<>();
        far.add(place("Highlands Coffee", "01", 21.03, 105.85));
        far.add(place("Highlands Coffee", "01", 21.02, 105.84));
        far.add(place("HIGHLANDS COFFEE", "48", 16.05, 108.20));
        far.add(place("Highlands Coffee", "56", 12.24, 109.19));
        elsewhere(far);

        GroupedPlaceSearchResponse result = search("highlands", 0);

        assertThat(result.getElsewhere()).extracting(PlaceResponse::getId)
                .containsExactly(far.get(0).getId(), far.get(2).getId());
        assertThat(result.isElsewhereTruncated()).isTrue();
    }

    @Test
    void neverEditsTheCachedAreaResults() {
        PlaceResponse cached = place("Phở Minh", "79", 10.7770, 106.7010);
        nearby(List.of(cached));
        when(placeService.areaKeywordTier(any(), any(), any(), any(), any()))
                .thenReturn(LuceneKeywordQuery.Match.EXACT);

        search("phở minh", 0);

        assertThat(cached.getProvinceName()).isNull();
        assertThat(cached.getDistance()).isNull();
    }

    private GroupedPlaceSearchResponse search(String keyword, int page) {
        return service.search(keyword, SAIGON_LAT, SAIGON_LNG, RADIUS, null, null, null, null,
                PlaceSearchSort.RELEVANCE, null, null, null, false, null, page, 20);
    }

    private void nearby(List<PlaceResponse> places) {
        when(placeService.searchPlaces(any(), eq(SAIGON_LAT), eq(SAIGON_LNG), eq(RADIUS), any(), any(), any(),
                any(), any(), any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt())).thenReturn(places);
    }

    private void elsewhere(List<PlaceResponse> places) {
        when(placeService.searchPlacesByNameOutside(any(), eq(SAIGON_LAT), eq(SAIGON_LNG), eq(RADIUS), any(),
                anyInt())).thenReturn(places);
    }

    private static PlaceResponse place(String title, String provinceCode, double latitude, double longitude) {
        return PlaceResponse.builder()
                .id(UUID.randomUUID())
                .title(title)
                .name(title)
                .provinceCode(provinceCode)
                .latitude(BigDecimal.valueOf(latitude))
                .longitude(BigDecimal.valueOf(longitude))
                .build();
    }
}

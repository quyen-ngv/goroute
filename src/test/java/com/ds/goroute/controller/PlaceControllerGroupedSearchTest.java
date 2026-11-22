package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.GroupedPlaceSearchResponse;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.service.PlaceGroupedSearchService;
import com.ds.goroute.service.PlaceMapTileService;
import com.ds.goroute.service.ProvinceNameResolver;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.type.PlaceSearchSort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceControllerGroupedSearchTest {

    private static final BigDecimal LAT = new BigDecimal("10.7769");
    private static final BigDecimal LNG = new BigDecimal("106.7009");

    private PlaceService placeService;
    private PlaceGroupedSearchService groupedSearchService;
    private PlaceController controller;

    @BeforeEach
    void setUp() {
        placeService = mock(PlaceService.class);
        groupedSearchService = mock(PlaceGroupedSearchService.class);
        controller = new PlaceController(placeService, mock(FoodService.class), mock(PlaceMapTileService.class),
                groupedSearchService, mock(ProvinceNameResolver.class));
        ReflectionTestUtils.setField(controller, "httpServletRequest", new MockHttpServletRequest());
    }

    @Test
    void groupedSearchPassesTheSameQueryAndWrapsBothGroups() {
        PlaceResponse far = PlaceResponse.builder().id(UUID.randomUUID()).title("Phở Thìn Bờ Hồ")
                .provinceCode("01").provinceName("Hà Nội").distance(1140.0).build();
        when(groupedSearchService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), any(), anyInt(), anyInt()))
                .thenReturn(GroupedPlaceSearchResponse.builder().nearby(List.of()).elsewhere(List.of(far))
                        .elsewhereTruncated(false).build());

        BaseResponse<GroupedPlaceSearchResponse> body = controller.searchPlacesGrouped(
                "phở thìn", LAT, LNG, new BigDecimal("30"), null, List.of("FOOD_AND_DRINK"), null, null,
                true, null, null, null, null, false, null, 0, 20, null).getBody();

        assertThat(body.getData().getElsewhere()).containsExactly(far);
        verify(groupedSearchService).search(eq("phở thìn"), eq(LAT), eq(LNG), eq(new BigDecimal("30")), eq(null),
                eq(List.of("FOOD_AND_DRINK")), eq(null), eq(null), eq(PlaceSearchSort.RATING), eq(null), eq(null),
                eq(null), eq(false), eq(null), eq(0), eq(20));
    }

    @Test
    void theFlatSearchStillReturnsAListWithoutProvinceNames() throws Exception {
        PlaceResponse local = PlaceResponse.builder().id(UUID.randomUUID()).title("Phở Minh")
                .provinceCode("79").latitude(LAT).longitude(LNG).build();
        when(placeService.searchPlaces(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), any(), anyInt(), anyInt())).thenReturn(List.of(local));

        Object body = controller.searchPlaces("phở", LAT, LNG, new BigDecimal("30"), null, null, null, null,
                false, null, null, null, null, false, null, 0, 20, null).getBody();
        String json = new ObjectMapper().writeValueAsString(body);

        assertThat(json).contains("\"data\":[{").doesNotContain("provinceName").doesNotContain("elsewhere");
    }
}

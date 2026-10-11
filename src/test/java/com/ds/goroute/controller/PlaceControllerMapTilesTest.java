package com.ds.goroute.controller;

import com.ds.goroute.service.FoodService;
import com.ds.goroute.service.PlaceMapTileService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.utils.MapTile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlaceControllerMapTilesTest {

    private PlaceMapTileService mapTileService;
    private PlaceController controller;

    @BeforeEach
    void setUp() {
        mapTileService = mock(PlaceMapTileService.class);
        when(mapTileService.getTiles(any(), any(), any())).thenReturn(List.of());
        controller = new PlaceController(mock(PlaceService.class), mock(FoodService.class), mapTileService);
        ReflectionTestUtils.setField(controller, "httpServletRequest", new MockHttpServletRequest());
    }

    @Test
    void readsTilesAsXyPairsAtTheZoomAndNormalisesSubTypes() {
        controller.getMapTiles(12, List.of("3252:1798", " 3253:1798 "), List.of("ATTRACTIONS"), List.of("beach"));

        verify(mapTileService).getTiles(
                eq(List.of(new MapTile(12, 3252, 1798), new MapTile(12, 3253, 1798))),
                eq(List.of("ATTRACTIONS")),
                eq(List.of("BEACH")));
    }

    @Test
    void refusesMalformedTiles() {
        assertThatThrownBy(() -> controller.getMapTiles(12, List.of("3252-1798"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.getMapTiles(12, List.of("a:b"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapTileService);
    }

    @Test
    void refusesTilesOutsideTheZoom() {
        assertThatThrownBy(() -> controller.getMapTiles(3, List.of("8:0"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapTileService);
    }

    @Test
    void refusesUnknownSubTypes() {
        assertThatThrownBy(() -> controller.getMapTiles(12, List.of("3252:1798"), null, List.of("VOLCANO")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

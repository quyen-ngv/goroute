package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.PlaceImagesDto;
import com.ds.goroute.dto.response.PlaceMapPinResponse;
import com.ds.goroute.dto.response.PlaceMapTileResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.PlaceMapTileService;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.TranslationLocale;
import com.ds.goroute.utils.MapTile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceMapTileServiceImplTest {

    private final MapTile city = new MapTile(12, 3252, 1798);
    private final MapTile sea = new MapTile(12, 3260, 1798);

    private PlaceSearchIndexService searchIndex;
    private PlaceRepository placeRepository;
    private PlaceTranslationService translations;
    private PlaceMapTileServiceImpl service;

    @BeforeEach
    void setUp() {
        searchIndex = mock(PlaceSearchIndexService.class);
        placeRepository = mock(PlaceRepository.class);
        translations = mock(PlaceTranslationService.class);
        when(translations.findByPlaceIds(any())).thenReturn(Map.of());
        service = new PlaceMapTileServiceImpl(searchIndex, placeRepository, translations,
                new ConcurrentMapCacheManager(PlaceMapTileService.CACHE));
    }

    @Test
    void answersEachTileInTheOrderAskedWithItsPins() throws Exception {
        Place lake = place("Hồ Gươm");
        when(searchIndex.findMapTilePlaceIds(anyCollection(), any(), any()))
                .thenReturn(Map.of(city, List.of(lake.getId()), sea, List.of()));
        when(placeRepository.findMapPinsByIds(anyList())).thenReturn(List.of(lake));

        List<PlaceMapTileResponse> tiles = service.getTiles(List.of(sea, city), null, null);

        assertThat(tiles).extracting(PlaceMapTileResponse::getX).containsExactly(sea.x(), city.x());
        assertThat(tiles.get(0).getPlaces()).isEmpty();
        PlaceMapPinResponse pin = tiles.get(1).getPlaces().get(0);
        assertThat(pin.getId()).isEqualTo(lake.getId());
        assertThat(pin.getTitle()).isEqualTo("Hồ Gươm");
        assertThat(pin.getPlaceGroup()).isEqualTo("ATTRACTIONS");
        assertThat(pin.getReviewCount()).isEqualTo(1200);
    }

    @Test
    void readsATileFromTheIndexOnceAndThenFromTheCache() throws Exception {
        Place lake = place("Hồ Gươm");
        when(searchIndex.findMapTilePlaceIds(anyCollection(), any(), any()))
                .thenReturn(Map.of(city, List.of(lake.getId())));
        when(placeRepository.findMapPinsByIds(anyList())).thenReturn(List.of(lake));

        service.getTiles(List.of(city), List.of("ATTRACTIONS"), null);
        List<PlaceMapTileResponse> again = service.getTiles(List.of(city), List.of("ATTRACTIONS"), null);

        assertThat(again.get(0).getPlaces()).hasSize(1);
        verify(searchIndex, times(1)).findMapTilePlaceIds(anyCollection(), any(), any());
    }

    @Test
    void keepsNoTileWhileTheIndexIsBeingRebuilt() throws Exception {
        when(searchIndex.isReindexRunning()).thenReturn(true);
        when(searchIndex.findMapTilePlaceIds(anyCollection(), any(), any())).thenReturn(Map.of());

        service.getTiles(List.of(city), null, null);
        service.getTiles(List.of(city), null, null);

        verify(searchIndex, times(2)).findMapTilePlaceIds(anyCollection(), any(), any());
    }

    @Test
    void anotherFilterIsAnotherTile() throws Exception {
        when(searchIndex.findMapTilePlaceIds(anyCollection(), any(), any())).thenReturn(Map.of());

        service.getTiles(List.of(city), List.of("ATTRACTIONS"), null);
        service.getTiles(List.of(city), List.of("FOOD_AND_DRINK"), null);
        service.getTiles(List.of(city), List.of("ATTRACTIONS"), List.of("BEACH"));

        verify(searchIndex, times(3)).findMapTilePlaceIds(anyCollection(), any(), any());
        verify(searchIndex).findMapTilePlaceIds(anyCollection(), eq(List.of("ATTRACTIONS")), eq(List.of("BEACH")));
    }

    @Test
    void namesAPinInTheTranslatedName() throws Exception {
        Place lake = place("Hồ Gươm");
        when(searchIndex.findMapTilePlaceIds(anyCollection(), any(), any()))
                .thenReturn(Map.of(city, List.of(lake.getId())));
        when(placeRepository.findMapPinsByIds(anyList())).thenReturn(List.of(lake));
        when(translations.findByPlaceIds(any())).thenReturn(Map.of(lake.getId(), List.of(
                PlaceTranslation.builder().placeId(lake.getId()).locale(TranslationLocale.EN)
                        .name("Hoan Kiem Lake").build())));

        List<PlaceMapTileResponse> tiles = service.getTiles(List.of(city), null, null);

        assertThat(tiles.get(0).getPlaces().get(0).getName()).isEqualTo("Hoan Kiem Lake");
    }

    @Test
    void dropsAPlaceTheIndexListsButTheDatabaseNoLongerShows() throws Exception {
        UUID hiddenSinceIndexed = UUID.randomUUID();
        when(searchIndex.findMapTilePlaceIds(anyCollection(), any(), any()))
                .thenReturn(Map.of(city, List.of(hiddenSinceIndexed)));
        when(placeRepository.findMapPinsByIds(anyList())).thenReturn(List.of());

        assertThat(service.getTiles(List.of(city), null, null).get(0).getPlaces()).isEmpty();
    }

    @Test
    void aPinCarriesItsAddressUsageAndFirstPhotos() throws Exception {
        Place lake = place("Hồ Gươm");
        lake.setAddress("Hàng Trống, Hoàn Kiếm");
        lake.setTripUseCount(12);
        lake.setCheckinCount(3);
        lake.setImages("[{\"image\":\"a.jpg\"},{\"title\":\"b\",\"image\":\"b.jpg\"},{\"image\":\"\"},"
                + "{\"image\":\"c.jpg\"},{\"image\":\"d.jpg\"},{\"image\":\"e.jpg\"},{\"image\":\"f.jpg\"}]");
        when(searchIndex.findMapTilePlaceIds(anyCollection(), any(), any()))
                .thenReturn(Map.of(city, List.of(lake.getId())));
        when(placeRepository.findMapPinsByIds(anyList())).thenReturn(List.of(lake));

        PlaceMapPinResponse pin = service.getTiles(List.of(city), null, null).get(0).getPlaces().get(0);

        assertThat(pin.getAddress()).isEqualTo("Hàng Trống, Hoàn Kiếm");
        assertThat(pin.getTripUseCount()).isEqualTo(12);
        assertThat(pin.getCheckinCount()).isEqualTo(3);
        assertThat(pin.getImages()).extracting(PlaceImagesDto::getImage)
                .containsExactly("a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg");
        // A client that requires a title gets a blank one rather than null.
        assertThat(pin.getImages().get(0).getTitle()).isEmpty();
    }

    @Test
    void malformedPhotosLeaveThePinWithout() {
        assertThat(PlaceMapTileServiceImpl.firstImages("not json")).isEmpty();
        assertThat(PlaceMapTileServiceImpl.firstImages(null)).isEmpty();
    }

    private static Place place(String title) {
        return Place.builder()
                .id(UUID.randomUUID())
                .title(title)
                .placeGroup(PlaceGroup.ATTRACTIONS)
                .latitude(new BigDecimal("21.0287"))
                .longitude(new BigDecimal("105.8524"))
                .reviewRating(new BigDecimal("4.6"))
                .reviewCount(1200)
                .build();
    }
}

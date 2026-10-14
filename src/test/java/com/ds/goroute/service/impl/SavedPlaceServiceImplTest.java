package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.SavePlaceRequest;
import com.ds.goroute.dto.response.RecentSavedPlaceResponse;
import com.ds.goroute.dto.response.SavedItemsOverviewResponse;
import com.ds.goroute.dto.response.SavedPlaceCategoryResponse;
import com.ds.goroute.dto.response.SavedPlaceResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.SavedCategory;
import com.ds.goroute.entity.SavedPlace;
import com.ds.goroute.entity.SavedPlaceMark;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.mapper.SavedCategoryMapper;
import com.ds.goroute.mapper.SavedPlaceMapper;
import com.ds.goroute.repository.LocationImageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavedPlaceServiceImplTest {
    private final SavedPlaceMapper savedPlaceMapper = mock(SavedPlaceMapper.class);
    private final PlaceMapper placeMapper = mock(PlaceMapper.class);
    private final LocationImageRepository locationImageRepository = mock(LocationImageRepository.class);
    private final SavedCategoryMapper savedCategoryMapper = mock(SavedCategoryMapper.class);
    private final SavedPlaceServiceImpl service = new SavedPlaceServiceImpl(
            savedPlaceMapper,
            savedCategoryMapper,
            placeMapper,
            locationImageRepository,
            new ObjectMapper());

    private final UUID userId = UUID.randomUUID();

    @Test
    void overviewReadsEverySavedItemWhileTheRecentListStaysCapped() {
        List<SavedPlace> manySaves = IntStream.range(0, SavedPlaceServiceImpl.MAX_LISTED_SAVED_ITEMS + 1)
                .mapToObj(i -> saved("ChIJ-google-" + i, "Place " + i, 16.0, 108.0))
                .toList();
        when(savedPlaceMapper.findAllByUserId(userId, null)).thenReturn(manySaves);

        SavedItemsOverviewResponse overview = service.getSavedItemsOverview(userId);
        service.getRecentSavedPlaces(userId);

        assertThat(overview.getSavedItems()).hasSize(SavedPlaceServiceImpl.MAX_LISTED_SAVED_ITEMS + 1);
        verify(savedPlaceMapper).findAllByUserId(userId, null);
        verify(savedPlaceMapper).findAllByUserId(userId, SavedPlaceServiceImpl.MAX_LISTED_SAVED_ITEMS);
    }

    @Test
    void recentSavedPlacesComeFromThePlaceRecordWithItsTouristArea() {
        UUID placeId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(savedPlaceMapper.findAllByUserId(userId, SavedPlaceServiceImpl.MAX_LISTED_SAVED_ITEMS)).thenReturn(List.of(
                saved(placeId.toString(), "Old name", 1.0, 1.0)));
        when(placeMapper.findSavedSummaryByIds(List.of(placeId))).thenReturn(List.of(Place.builder()
                .id(placeId)
                .title("Bà Nà Hills")
                .address("Hoà Vang, Đà Nẵng")
                .latitude(new BigDecimal("15.9977"))
                .longitude(new BigDecimal("107.9886"))
                .reviewRating(new BigDecimal("4.4"))
                .adjustedRating(new BigDecimal("4.6"))
                .thumbnail("https://img/ba-na.jpg")
                .category("Theme park")
                .locationImageId(areaId)
                .build()));
        when(locationImageRepository.findNamesByIds(Set.of(areaId))).thenReturn(List.of(
                LocationImage.builder().id(areaId).fullAddress("Đà Nẵng").build()));

        RecentSavedPlaceResponse place = service.getRecentSavedPlaces(userId).get(0);

        assertThat(place.getName()).isEqualTo("Bà Nà Hills");
        assertThat(place.getAddress()).isEqualTo("Hoà Vang, Đà Nẵng");
        assertThat(place.getLat()).isEqualTo(15.9977);
        assertThat(place.getLng()).isEqualTo(107.9886);
        assertThat(place.getRating()).isEqualTo(4.6);
        assertThat(place.getPhotoUrl()).isEqualTo("https://img/ba-na.jpg");
        assertThat(place.getPlaceCategory()).isEqualTo("Theme park");
        assertThat(place.getLocationImageId()).isEqualTo(areaId);
        assertThat(place.getLocationImageName()).isEqualTo("Đà Nẵng");
    }

    @Test
    void placeOutsideEveryTouristAreaKeepsNoArea() {
        UUID placeId = UUID.randomUUID();
        when(savedPlaceMapper.findAllByUserId(userId, SavedPlaceServiceImpl.MAX_LISTED_SAVED_ITEMS)).thenReturn(List.of(
                saved(placeId.toString(), "Somewhere", 10.0, 105.0)));
        when(placeMapper.findSavedSummaryByIds(List.of(placeId))).thenReturn(List.of(Place.builder()
                .id(placeId)
                .title("Somewhere")
                .build()));

        RecentSavedPlaceResponse place = service.getRecentSavedPlaces(userId).get(0);

        assertThat(place.getLocationImageId()).isNull();
        assertThat(place.getLocationImageName()).isNull();
        verify(locationImageRepository, never()).findNamesByIds(any());
    }

    @Test
    void placeNoLongerInTheCatalogueKeepsWhatWasSaved() {
        UUID placeId = UUID.randomUUID();
        when(savedPlaceMapper.findAllByUserId(userId, SavedPlaceServiceImpl.MAX_LISTED_SAVED_ITEMS)).thenReturn(List.of(
                saved(placeId.toString(), "Saved name", 16.0, 108.0)));
        when(placeMapper.findSavedSummaryByIds(anyList())).thenReturn(List.of());

        RecentSavedPlaceResponse place = service.getRecentSavedPlaces(userId).get(0);

        assertThat(place.getName()).isEqualTo("Saved name");
        assertThat(place.getLat()).isEqualTo(16.0);
        assertThat(place.getLocationImageId()).isNull();
    }

    @Test
    void kindOfPlaceFromAVideoIsNotASavedCategory() {
        UUID placeId = UUID.randomUUID();
        when(savedPlaceMapper.findSocialSavedPlacesByUserId(userId)).thenReturn(List.of(SocialLocationJob.builder()
                .completedAt(LocalDateTime.now())
                .resultPayload("""
                        {"extraction":{"candidates":[{"name":"Cầu Rồng","category":"Bridge",
                          "description":"Phun lửa tối cuối tuần",
                          "mapSearch":{"candidates":[{"title":"Cầu Rồng","category":"Bridge",
                            "placeMapping":{"placeId":"PLACE_ID"}}]}}]}}
                        """.replace("PLACE_ID", placeId.toString()))
                .build()));
        when(placeMapper.findSavedSummaryByIds(anyList())).thenReturn(List.of());

        RecentSavedPlaceResponse place = service.getRecentSavedPlaces(userId).get(0);

        assertThat(place.getCategories()).extracting(SavedPlaceCategoryResponse::getName).containsOnlyNulls();
        assertThat(place.getPlaceCategory()).isEqualTo("Bridge");
    }

    @Test
    void savedIdThatIsNotACatalogueIdIsNotLookedUp() {
        when(savedPlaceMapper.findAllByUserId(userId, SavedPlaceServiceImpl.MAX_LISTED_SAVED_ITEMS)).thenReturn(List.of(
                saved("ChIJ-google-id", "Google place", 16.0, 108.0)));

        RecentSavedPlaceResponse place = service.getRecentSavedPlaces(userId).get(0);

        assertThat(place.getName()).isEqualTo("Google place");
        verify(placeMapper, never()).findSavedSummaryByIds(anyList());
    }

    @Test
    void savingIntoAnExistingCategoryUsesItsSpelling() {
        when(savedCategoryMapper.findByUserAndName(userId, "ăn uống")).thenReturn(SavedCategory.builder()
                .id(UUID.randomUUID()).userId(userId).name("Ăn uống").build());

        SavedPlaceResponse saved = service.savePlace(userId, request("PLACE", " ăn uống "));

        assertThat(saved.getCategory()).isEqualTo("Ăn uống");
        verify(savedCategoryMapper, never()).insertIfAbsent(any(), any(), any(), any());
        verify(savedPlaceMapper).findByUserIdAndPlaceId(userId, "place-1", "PLACE", "Ăn uống");
    }

    @Test
    void savingIntoANewCategoryCreatesIt() {
        SavedPlaceResponse saved = service.savePlace(userId, request("PLACE", "Cà phê"));

        assertThat(saved.getCategory()).isEqualTo("Cà phê");
        verify(savedCategoryMapper).insertIfAbsent(any(UUID.class), eq(userId), eq("Cà phê"), any());
    }

    @Test
    void savingWithoutACategoryCreatesNone() {
        service.savePlace(userId, request("PLACE", "  "));

        verify(savedCategoryMapper, never()).insertIfAbsent(any(), any(), any(), any());
        ArgumentCaptor<SavedPlace> inserted = ArgumentCaptor.forClass(SavedPlace.class);
        verify(savedPlaceMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getCategory()).isNull();
    }

    @Test
    void mapPlaceWithoutCoordinatesIsRefused() {
        SavePlaceRequest request = request("MAP_PLACE", "Cà phê");
        request.setLat(null);

        assertThatThrownBy(() -> service.savePlace(userId, request))
                .isInstanceOf(BusinessException.class);
        verify(savedPlaceMapper, never()).insert(any());
    }

    @Test
    void mapPlaceIsSavedWithItsType() {
        SavedPlaceResponse saved = service.savePlace(userId, request("map_place", "Cà phê"));

        assertThat(saved.getItemType()).isEqualTo("MAP_PLACE");
    }

    @Test
    void removingOffTheWholeListDeletesEverySaveAndKeepsVideoPlacesOff() {
        service.removeFromList(userId, List.of(" place-1 ", "place-1", "place-2"), null);

        verify(savedPlaceMapper).deletePlaceSaves(userId, List.of("place-1", "place-2"), null);
        verify(savedPlaceMapper).markRemoved(eq(userId), eq(List.of("place-1", "place-2")), any());
    }

    @Test
    void removingOutOfOneCategoryLeavesThePlaceOnTheList() {
        service.removeFromList(userId, List.of("place-1"), " Ăn uống ");

        verify(savedPlaceMapper).deletePlaceSaves(userId, List.of("place-1"), "Ăn uống");
        verify(savedPlaceMapper, never()).markRemoved(any(), anyList(), any());
    }

    @Test
    void videoPlaceRemovedAfterTheVideoStaysOffButANewerVideoBringsItBack() {
        UUID removedId = UUID.randomUUID();
        UUID laterId = UUID.randomUUID();
        LocalDateTime removedAt = LocalDateTime.now().minusDays(1);
        when(savedPlaceMapper.findMarksByUserId(userId)).thenReturn(List.of(
                SavedPlaceMark.builder().userId(userId).placeId(removedId.toString()).removedAt(removedAt).build(),
                SavedPlaceMark.builder().userId(userId).placeId(laterId.toString()).removedAt(removedAt).build()));
        when(savedPlaceMapper.findSocialSavedPlacesByUserId(userId)).thenReturn(List.of(
                videoJob(laterId, removedAt.plusHours(1)),
                videoJob(removedId, removedAt.minusHours(1))));
        when(placeMapper.findSavedSummaryByIds(anyList())).thenReturn(List.of());

        List<RecentSavedPlaceResponse> places = service.getRecentSavedPlaces(userId);

        assertThat(places).extracting(RecentSavedPlaceResponse::getPlaceId).containsExactly(laterId.toString());
    }

    @Test
    void visitedPlaceCarriesWhenItWasMarked() {
        UUID placeId = UUID.randomUUID();
        LocalDateTime visitedAt = LocalDateTime.now().minusDays(2);
        when(savedPlaceMapper.findAllByUserId(userId, SavedPlaceServiceImpl.MAX_LISTED_SAVED_ITEMS)).thenReturn(List.of(
                saved(placeId.toString(), "Hồ Gươm", 21.03, 105.85)));
        when(savedPlaceMapper.findMarksByUserId(userId)).thenReturn(List.of(
                SavedPlaceMark.builder().userId(userId).placeId(placeId.toString()).visitedAt(visitedAt).build()));

        RecentSavedPlaceResponse place = service.getRecentSavedPlaces(userId).get(0);

        assertThat(place.getVisitedAt()).isEqualTo(visitedAt);
    }

    @Test
    void markingAndUnmarkingVisited() {
        service.setVisited(userId, " place-1 ", true);
        service.setVisited(userId, "place-1", false);

        verify(savedPlaceMapper).markVisited(eq(userId), eq("place-1"), any());
        verify(savedPlaceMapper).clearVisited(userId, "place-1");
    }

    private SocialLocationJob videoJob(UUID placeId, LocalDateTime completedAt) {
        return SocialLocationJob.builder()
                .completedAt(completedAt)
                .resultPayload("""
                        {"extraction":{"candidates":[{"name":"Cầu Rồng",
                          "mapSearch":{"candidates":[{"title":"Cầu Rồng",
                            "placeMapping":{"placeId":"PLACE_ID"}}]}}]}}
                        """.replace("PLACE_ID", placeId.toString()))
                .build();
    }

    private SavePlaceRequest request(String itemType, String category) {
        return SavePlaceRequest.builder()
                .placeId("place-1")
                .itemType(itemType)
                .name("Cộng Cà Phê")
                .lat(21.03)
                .lng(105.85)
                .category(category)
                .build();
    }

    private SavedPlace saved(String placeId, String name, Double lat, Double lng) {
        return SavedPlace.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .placeId(placeId)
                .itemType("PLACE")
                .name(name)
                .lat(lat)
                .lng(lng)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

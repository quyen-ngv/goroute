package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.RecentSavedPlaceResponse;
import com.ds.goroute.dto.response.SavedPlaceCategoryResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.SavedPlace;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.mapper.SavedPlaceMapper;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavedPlaceServiceImplTest {
    private final SavedPlaceMapper savedPlaceMapper = mock(SavedPlaceMapper.class);
    private final SocialLocationJobMapper socialLocationJobMapper = mock(SocialLocationJobMapper.class);
    private final PlaceMapper placeMapper = mock(PlaceMapper.class);
    private final LocationImageRepository locationImageRepository = mock(LocationImageRepository.class);
    private final SavedPlaceServiceImpl service = new SavedPlaceServiceImpl(
            savedPlaceMapper,
            socialLocationJobMapper,
            placeMapper,
            locationImageRepository,
            mock(ImageStorageCleanupService.class),
            new ObjectMapper());

    private final UUID userId = UUID.randomUUID();

    @Test
    void recentSavedPlacesComeFromThePlaceRecordWithItsTouristArea() {
        UUID placeId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        when(savedPlaceMapper.findAllByUserId(userId)).thenReturn(List.of(
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
        when(savedPlaceMapper.findAllByUserId(userId)).thenReturn(List.of(
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
        when(savedPlaceMapper.findAllByUserId(userId)).thenReturn(List.of(
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
        when(socialLocationJobMapper.findAllCompletedByUserId(userId)).thenReturn(List.of(SocialLocationJob.builder()
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
        when(savedPlaceMapper.findAllByUserId(userId)).thenReturn(List.of(
                saved("ChIJ-google-id", "Google place", 16.0, 108.0)));

        RecentSavedPlaceResponse place = service.getRecentSavedPlaces(userId).get(0);

        assertThat(place.getName()).isEqualTo("Google place");
        verify(placeMapper, never()).findSavedSummaryByIds(anyList());
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

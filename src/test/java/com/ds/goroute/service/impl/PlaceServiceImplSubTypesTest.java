package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.repository.PlaceSourceRepository;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.service.LocationAreaService;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceSubType;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How sub-types survive imports, refreshes and console edits. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceServiceImplSubTypesTest {

    @Mock private PlaceRepository placeRepository;
    @Mock private PlaceSourceRepository placeSourceRepository;
    @Mock private PlaceSearchIndexService placeSearchIndexService;
    @Mock private PlaceTranslationService placeTranslationService;
    @Mock private FoodRepository foodRepository;
    @Mock private PlaceReviewRepository placeReviewRepository;
    @Mock private GeoService geoService;
    @Mock private LocationAreaService locationAreaService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private PlaceServiceImpl service;

    @Test
    void newImportStoresOnlyKindsThatFitTheGroup() {
        PlaceResponse response = service.importPlace(importRequest(List.of("halal", "BEACH", "nonsense", "CAFE")));

        ArgumentCaptor<Place> inserted = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).insert(inserted.capture());
        assertThat(inserted.getValue().getSubTypes()).containsExactly("CAFE", "HALAL");
        assertThat(response.getSubTypes()).containsExactly("CAFE", "HALAL");
    }

    @Test
    void refreshFillsAPlaceThatHasNoKindsYet() {
        Place existing = place(new String[0]);
        when(placeRepository.findByPlaceId("g-1")).thenReturn(existing);

        service.importPlace(importRequest(List.of("CAFE")));

        verify(placeRepository).updateSubTypes(existing.getId(), new String[] {"CAFE"});
    }

    @Test
    void refreshNeverOverwritesKindsAnOperatorChose() {
        Place existing = place(new String[] {"TEA"});
        when(placeRepository.findByPlaceId("g-1")).thenReturn(existing);

        PlaceResponse response = service.importPlace(importRequest(List.of("CAFE")));

        verify(placeRepository, never()).updateSubTypes(any(), any());
        assertThat(response.getSubTypes()).containsExactly("TEA");
    }

    @Test
    void consoleEditWithoutSubTypesKeepsThem() {
        Place stored = place(new String[] {"HALAL"});
        when(placeRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        PlaceResponse response = service.updatePlace(stored.getId(), updateRequest(null));

        verify(placeRepository, never()).updateSubTypes(any(), any());
        assertThat(response.getSubTypes()).containsExactly("HALAL");
    }

    @Test
    void consoleEditReplacesSubTypes() {
        Place stored = place(new String[] {"HALAL"});
        when(placeRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        service.updatePlace(stored.getId(), updateRequest(List.of(PlaceSubType.VEGETARIAN, PlaceSubType.CAFE)));

        verify(placeRepository).updateSubTypes(eq(stored.getId()), eq(new String[] {"CAFE", "VEGETARIAN"}));
    }

    @Test
    void consoleEditRejectsAKindThatDoesNotFitTheGroup() {
        Place stored = place(new String[0]);
        when(placeRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.updatePlace(stored.getId(), updateRequest(List.of(PlaceSubType.BEACH))))
                .isInstanceOf(BusinessException.class);
        verify(placeRepository, never()).update(any());
    }

    private static ImportPlaceRequest importRequest(List<String> subTypes) {
        return ImportPlaceRequest.builder()
                .placeId("g-1")
                .title("Cafe")
                .placeGroup("FOOD_AND_DRINK")
                .subTypes(subTypes)
                .latitude(BigDecimal.TEN)
                .longitude(BigDecimal.valueOf(106))
                .build();
    }

    private static UpdatePlaceRequest updateRequest(List<PlaceSubType> subTypes) {
        return UpdatePlaceRequest.builder()
                .title("Cafe")
                .placeGroup("FOOD_AND_DRINK")
                .subTypes(subTypes)
                .latitude(BigDecimal.TEN)
                .longitude(BigDecimal.valueOf(106))
                .build();
    }

    private static Place place(String[] subTypes) {
        return Place.builder()
                .id(UUID.randomUUID())
                .placeId("g-1")
                .title("Cafe")
                .placeGroup(PlaceGroup.FOOD_AND_DRINK)
                .subTypes(subTypes)
                .latitude(BigDecimal.TEN)
                .longitude(BigDecimal.valueOf(106))
                .visibilityStatus(PlaceVisibilityStatus.ACTIVE)
                .build();
    }
}

package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateCityStoryRequest;
import com.ds.goroute.dto.response.CityStoryFeedResponse;
import com.ds.goroute.dto.response.CityStoryGroupResponse;
import com.ds.goroute.dto.response.CityStoryItemResponse;
import com.ds.goroute.dto.response.CityWeatherResponse;
import com.ds.goroute.entity.CityStory;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.entity.Place;
import com.ds.goroute.mapper.CityStoryMapper;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.CityWeatherService;
import com.ds.goroute.service.ImageStorageCleanupService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** City Story feed: shared story groups cached briefly, viewer marks fresh (plan 2.5, 5.3). */
class CityStoryServiceImplFeedTest {

    private final CityStoryMapper mapper = mock(CityStoryMapper.class);
    private final LocationImageRepository locations = mock(LocationImageRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final CityWeatherService weather = mock(CityWeatherService.class);
    private final Executor sameThread = Runnable::run;

    private final CityStoryServiceImpl service = new CityStoryServiceImpl(
            mapper, locations, places, mock(ImageStorageCleanupService.class), weather, sameThread);

    private final UUID locationId = UUID.randomUUID();
    private final UUID placeId = UUID.randomUUID();
    private final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    private final LocationImage hanoi = LocationImage.builder().id(locationId).fullAddress("Ha Noi")
            .citySlug("ha-noi").imageUrl("https://cdn/hn.jpg").build();
    private final CityStory older = CityStory.builder().id(UUID.randomUUID()).locationImageId(locationId)
            .placeId(placeId).likeCount(2).createdAt(now.minusHours(3)).build();
    private final CityStory newer = CityStory.builder().id(UUID.randomUUID()).locationImageId(locationId)
            .placeId(placeId).likeCount(0).createdAt(now.minusHours(1)).build();

    private void twoStoriesInHanoi() {
        when(mapper.selectActiveSince(any())).thenReturn(List.of(newer, older));
        when(locations.findAll()).thenReturn(List.of(hanoi));
        when(places.findCardsByIds(List.of(placeId))).thenReturn(List.of(Place.builder().id(placeId).title("Hoan Kiem").build()));
        when(weather.findWeatherQuietly(hanoi)).thenReturn(CityWeatherResponse.builder().build());
    }

    @Test
    void viewersShareOneLoadOfTheGroupsButEachGetsTheirOwnMarks() {
        twoStoriesInHanoi();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(mapper.selectViewedStoryIdsSince(eq(alice), any())).thenReturn(List.of(older.getId(), newer.getId()));
        when(mapper.selectLikedStoryIdsSince(eq(alice), any())).thenReturn(List.of(older.getId()));

        CityStoryFeedResponse forAlice = service.getFeed(alice);
        CityStoryFeedResponse forBob = service.getFeed(bob);

        CityStoryGroupResponse aliceGroup = forAlice.getCities().getFirst();
        assertThat(aliceGroup.isHasUnviewedStories()).isFalse();
        assertThat(aliceGroup.getWeather()).isNotNull();
        assertThat(aliceGroup.getStories()).extracting(CityStoryItemResponse::getId)
                .as("oldest first").containsExactly(older.getId(), newer.getId());
        assertThat(aliceGroup.getStories()).extracting(CityStoryItemResponse::isHasLiked).containsExactly(true, false);
        assertThat(aliceGroup.getStories()).allSatisfy(item -> assertThat(item.getPlace().getTitle()).isEqualTo("Hoan Kiem"));

        CityStoryGroupResponse bobGroup = forBob.getCities().getFirst();
        assertThat(bobGroup.isHasUnviewedStories()).isTrue();
        assertThat(bobGroup.getStories()).extracting(CityStoryItemResponse::isHasLiked).containsOnly(false);

        verify(mapper, times(1)).selectActiveSince(any());
        verify(locations, times(1)).findAll();
        verify(places, times(1)).findCardsByIds(List.of(placeId));
        verify(places, never()).findById(any());
        verify(weather, times(1)).findWeatherQuietly(hanoi);
        verify(mapper, times(1)).selectViewedStoryIdsSince(eq(bob), any());
    }

    @Test
    void aGuestReadsNoViewerMarks() {
        twoStoriesInHanoi();

        CityStoryFeedResponse feed = service.getFeed(null);

        assertThat(feed.getCities()).singleElement()
                .satisfies(group -> assertThat(group.isHasUnviewedStories()).isTrue());
        verify(mapper, never()).selectViewedStoryIdsSince(any(), any());
        verify(mapper, never()).selectLikedStoryIdsSince(any(), any());
    }

    @Test
    void aNewStoryClearsTheSharedCopy() {
        twoStoriesInHanoi();
        service.getFeed(null);
        when(locations.findById(locationId)).thenReturn(Optional.of(hanoi));
        CreateCityStoryRequest request = new CreateCityStoryRequest();
        request.setImageUrl("https://cdn/new.jpg");

        service.createStory(locationId, request);
        service.getFeed(null);

        verify(mapper, times(2)).selectActiveSince(any());
    }

    @Test
    void noActiveStoriesMeansNoFurtherReads() {
        when(mapper.selectActiveSince(any())).thenReturn(List.of());

        assertThat(service.getFeed(UUID.randomUUID()).getCities()).isEmpty();

        verify(locations, never()).findAll();
        verify(weather, never()).findWeatherQuietly(any());
        verify(mapper, never()).selectViewedStoryIdsSince(any(), any());
    }
}

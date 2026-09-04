package com.ds.goroute.job;

import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.WeatherSnapshotProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherCacheRefreshJobTest {

    @Mock
    private LocationImageRepository locationImageRepository;

    @Mock
    private WeatherSnapshotProvider weatherSnapshotProvider;

    @InjectMocks
    private WeatherCacheRefreshJob job;

    @Test
    void refreshesEachDistinctGridCellAndSkipsLocationsWithoutCoordinates() {
        LocationImage firstCity = location("10.001", "106.001");
        LocationImage sameCell = location("10.004", "106.004");
        LocationImage secondCity = location("10.016", "106.016");
        LocationImage withoutCoordinates = LocationImage.builder().build();
        when(locationImageRepository.findAll())
            .thenReturn(List.of(firstCity, sameCell, secondCity, withoutCoordinates));

        job.refreshWeatherCache();

        verify(weatherSnapshotProvider).refreshSnapshot(
            new BigDecimal("10.001"), new BigDecimal("106.001"));
        verify(weatherSnapshotProvider).refreshSnapshot(
            new BigDecimal("10.016"), new BigDecimal("106.016"));
        verifyNoMoreInteractions(weatherSnapshotProvider);
    }

    @Test
    void continuesRefreshingOtherCellsWhenOneUpstreamCallFails() {
        LocationImage failedCity = location("10.001", "106.001");
        LocationImage healthyCity = location("10.016", "106.016");
        when(locationImageRepository.findAll()).thenReturn(List.of(failedCity, healthyCity));
        doThrow(new RestClientException("upstream unavailable"))
            .when(weatherSnapshotProvider)
            .refreshSnapshot(new BigDecimal("10.001"), new BigDecimal("106.001"));

        job.refreshWeatherCache();

        verify(weatherSnapshotProvider).refreshSnapshot(
            new BigDecimal("10.001"), new BigDecimal("106.001"));
        verify(weatherSnapshotProvider).refreshSnapshot(
            new BigDecimal("10.016"), new BigDecimal("106.016"));
    }

    private static LocationImage location(String latitude, String longitude) {
        return LocationImage.builder()
            .latitude(new BigDecimal(latitude))
            .longitude(new BigDecimal(longitude))
            .build();
    }
}

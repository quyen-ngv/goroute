package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateNationwidePlaceImportJobRequest;
import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.NationwidePlaceImportRequest;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceImportJob;
import com.ds.goroute.entity.PlaceImportJobItem;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.thirdparty.scrape.ScrapeJobTriggerResponse;
import com.ds.goroute.thirdparty.scrape.ScrapeNationwideJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.PlaceImportJobStatus;
import com.ds.goroute.type.PlaceImportSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NationwidePlaceImportJobServiceTest {
    private PlaceImportJobMapper jobMapper;
    private PlaceRepository placeRepository;
    private PlaceService placeService;
    private PlaceReviewService placeReviewService;
    private PlaceReviewScoreCalculator scoreCalculator;
    private ScrapeServiceClient scrapeServiceClient;
    private NationwidePlaceImportJobService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(PlaceImportJobMapper.class);
        placeRepository = mock(PlaceRepository.class);
        placeService = mock(PlaceService.class);
        placeReviewService = mock(PlaceReviewService.class);
        scoreCalculator = mock(PlaceReviewScoreCalculator.class);
        scrapeServiceClient = mock(ScrapeServiceClient.class);
        service = new NationwidePlaceImportJobService(
                jobMapper, placeRepository, placeService, placeReviewService,
                scoreCalculator, scrapeServiceClient, new ObjectMapper());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://goroute-app:8080");
        ReflectionTestUtils.setField(service, "callbackToken", "internal-token");
    }

    @Test
    void triggerPassesGoogleRatingThresholdToPythonWorker() {
        ScrapeJobTriggerResponse trigger = new ScrapeJobTriggerResponse();
        trigger.setJobId("python-job");
        when(scrapeServiceClient.triggerNationwideJob(any())).thenReturn(trigger);

        service.trigger(CreateNationwidePlaceImportJobRequest.builder()
                .minGoogleRating(BigDecimal.valueOf(4.2))
                .latitude(BigDecimal.valueOf(10.123456))
                .longitude(BigDecimal.valueOf(106.654321))
                .radiusKm(BigDecimal.valueOf(5))
                .searchZoom(13)
                .build());

        ArgumentCaptor<ScrapeNationwideJobRequest> requestCaptor =
                ArgumentCaptor.forClass(ScrapeNationwideJobRequest.class);
        verify(scrapeServiceClient).triggerNationwideJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getMinGoogleRating())
                .isEqualByComparingTo("4.2");
        assertThat(requestCaptor.getValue().getLatitude()).isEqualByComparingTo("10.123456");
        assertThat(requestCaptor.getValue().getLongitude()).isEqualByComparingTo("106.654321");
        assertThat(requestCaptor.getValue().getRadiusKm()).isEqualByComparingTo("5");
        assertThat(requestCaptor.getValue().getSearchZoom()).isEqualTo(13);
    }

    @Test
    void insufficientReviewPlaceIsPersistedInactiveWithOneImageAndNoReviews() {
        UUID jobId = UUID.randomUUID();
        UUID importedPlaceId = UUID.randomUUID();
        PlaceImportJob job = PlaceImportJob.builder()
                .id(jobId)
                .sourceType(PlaceImportSourceType.NATIONWIDE)
                .status(PlaceImportJobStatus.PROCESSING)
                .minReviewCount(101)
                .minAdjustedRating(BigDecimal.valueOf(3))
                .maxReviews(200)
                .selectedReviews(20)
                .requestPayload("{\"minGoogleRating\":4.0}")
                .build();
        when(jobMapper.findJobById(jobId)).thenReturn(job);
        when(placeService.importPlace(any())).thenReturn(PlaceResponse.builder().id(importedPlaceId).build());
        when(placeRepository.findById(importedPlaceId))
                .thenReturn(Optional.of(Place.builder().id(importedPlaceId).build()));

        ImportPlaceRequest place = ImportPlaceRequest.builder()
                .placeId("google-1")
                .title("New restaurant")
                .latitude(BigDecimal.TEN)
                .longitude(BigDecimal.valueOf(106))
                .reviewCount(20)
                .reviewRating(BigDecimal.valueOf(4.8))
                .images("[{\"image\":\"one\"},{\"image\":\"two\"}]")
                .userReviews("should-not-be-imported")
                .build();
        var response = service.importCandidate(NationwidePlaceImportRequest.builder()
                .jobId(jobId)
                .pythonJobId("python-job")
                .regionCode("test")
                .regionName("Test City")
                .place(place)
                .reviews(List.of())
                .build());

        assertThat(response.isImported()).isTrue();
        assertThat(response.getOutcome()).isEqualTo("SAVED_INACTIVE_REVIEW_COUNT");
        assertThat(place.getVisibilityStatus()).isEqualTo("INACTIVE");
        assertThat(place.getImages()).isEqualTo("[{\"image\":\"one\"}]");
        assertThat(place.getUserReviews()).isNull();
        verify(placeReviewService, never()).batchInsertReviews(any());
        verify(scoreCalculator, never()).scoreInputs(any(), anyInt(), any());

        ArgumentCaptor<PlaceImportJobItem> itemCaptor = ArgumentCaptor.forClass(PlaceImportJobItem.class);
        verify(jobMapper).updateItem(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getImportedPlaceId()).isEqualTo(importedPlaceId);
        assertThat(itemCaptor.getValue().getOutcomeReason()).isEqualTo("SAVED_INACTIVE_REVIEW_COUNT");
    }
}
